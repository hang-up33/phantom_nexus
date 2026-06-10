package com.phantomnexus.shared.schema;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.GuardHeight;
import com.phantomnexus.shared.types.Move;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * キャラクター JSON のローダ（Task 16 / Task 24）。**データ I/O の単一の真実**（{@code Shared/Schema}）。
 *
 * <p>{@code Assets/Characters/<id>.json} を LibGDX 組込みの {@link Json}（追加ライブラリ無し）で
 * {@link Character} POJO へデシリアライズし、必須フィールドを検証する。Task 24 で技定義を
 * {@code normalMoves[]} / {@code specialMoves[]} の配列形式に拡張した。
 *
 * <p>旧形式（Task 24 以前）の JSON（{@code normalAttack} / {@code specialMove} 単体フィールド）は
 * {@link #migrateIfLegacy(Character)} で配列形式へ自動移行する（後方互換）。
 *
 * <p>{@code GameRuntime} / {@code Battle} は本ローダ経由でのみデータを取得し、直接 JSON を読まない
 * （CLAUDE.md「データモデルの単一の真実」）。
 */
public final class CharacterLoader {

    private CharacterLoader() {
        // ユーティリティ（インスタンス化禁止）
    }

    private static final String BASE_PATH = "Characters/";

    /** 通常技の許可ボタン種別（大文字小文字正規化後に照合）。 */
    private static final Set<String> VALID_BUTTONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("light", "medium", "heavy")));

    /**
     * 有効な必殺技コマンド名（{@code Command.name()} と一致する文字列）。
     * {@code Shared} から {@code GameRuntime/Input.Command} への依存を避け、ここに列挙する。
     * 新コマンドを {@code Command} enum に追加したら本セットも同時に更新すること。
     */
    private static final Set<String> VALID_COMMANDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("HADOUKEN", "CHARGE_SHOT", "DOWN_ATTACK")));

    /**
     * 旧形式モーション記法 → {@code Command.name()} の変換テーブル（後方互換マイグレーション用）。
     * Task 20 以前の JSON が波動拳コマンドを "236A"/"236B"/"236C" などで記述していた場合に対応する。
     * キーは大文字で格納し、照合時も {@code toUpperCase()} して使う。
     */
    private static final Map<String, String> LEGACY_COMMAND_NOTATION;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("236A", "HADOUKEN");
        m.put("236B", "HADOUKEN");
        m.put("236C", "HADOUKEN");
        LEGACY_COMMAND_NOTATION = Collections.unmodifiableMap(m);
    }

    /**
     * 指定 ID のキャラクターを {@code Characters/<id>.json} から読み込み、検証して返す。
     *
     * @param id キャラ ID（ファイル名 {@code <id>.json} に対応）
     * @return 検証済みの {@link Character}
     * @throws SchemaException ファイル不在・解析失敗・必須フィールド不正のいずれか
     */
    public static Character load(String id) {
        String path = BASE_PATH + id + ".json";
        FileHandle file = Gdx.files.classpath(path);
        if (!file.exists()) {
            throw new SchemaException("キャラ JSON が見つかりません: " + path);
        }
        Character character;
        try {
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            character = json.fromJson(Character.class, file);
        } catch (RuntimeException e) {
            throw new SchemaException("キャラ JSON の解析に失敗: " + path + " (" + e.getMessage() + ")", e);
        }
        if (character == null) {
            throw new SchemaException("キャラ JSON が空です: " + path);
        }
        // 旧形式（normalAttack / specialMove）を新形式配列へ移行してから検証する（後方互換）。
        migrateIfLegacy(character, path);
        validate(character, path);
        Gdx.app.log("CharacterLoader", "読み込み成功: " + path + " (" + character.getName() + ")");
        return character;
    }

    /**
     * Task 24 以前の旧形式 JSON（{@code normalAttack} / {@code specialMove} 単体フィールド）を
     * 新形式の配列（{@code normalMoves[]} / {@code specialMoves[]}）へ自動移行する。
     *
     * <ul>
     *   <li>{@code normalMoves} が未設定かつ旧 {@code normalAttack} が存在する場合：
     *       {@code normalAttack} の {@code button} を {@code "light"} に設定し、
     *       {@code normalMoves[0]} として配列を生成する。</li>
     *   <li>{@code specialMoves} が未設定かつ旧 {@code specialMove} が存在する場合：
     *       {@code specialMoves[0]} として配列を生成する（{@code command} はそのまま維持）。</li>
     * </ul>
     */
    private static void migrateIfLegacy(Character c, String src) {
        boolean needsMigration = false;
        // normalAttack → normalMoves[0] (button="light") への移行
        if ((c.getNormalMoves() == null || c.getNormalMoves().length == 0) && c.legacyNormalAttack() != null) {
            Move legacy = c.legacyNormalAttack();
            // 旧形式は button フィールドを持たないため "light" をデフォルト値として注入する。
            if (legacy.getButton() == null || legacy.getButton().isEmpty()) {
                legacy.setButton("light");
            }
            c.setNormalMoves(new Move[]{legacy});
            needsMigration = true;
        }
        // specialMove → specialMoves[0] への移行。
        // 旧モーション記法（"236A" 等）があれば Command.name() 形式（"HADOUKEN" 等）へ正規化してから移行する。
        // 未知コマンド（正規化後も VALID_COMMANDS 外）は移行対象外とし validate() での SchemaException を防ぐ。
        if ((c.getSpecialMoves() == null || c.getSpecialMoves().length == 0) && c.legacySpecialMove() != null) {
            Move legacy = c.legacySpecialMove();
            String legacyCmd = legacy.getCommand();
            if (legacyCmd != null) {
                String upper = legacyCmd.trim().toUpperCase();
                String mapped = LEGACY_COMMAND_NOTATION.get(upper);
                if (mapped != null) {
                    legacy.setCommand(mapped);
                    legacyCmd = mapped;
                    Gdx.app.log("CharacterLoader",
                            "旧コマンド記法 '" + upper + "' を '" + mapped + "' へ変換しました: " + src);
                }
            }
            if (legacyCmd != null && VALID_COMMANDS.contains(legacyCmd.trim().toUpperCase())) {
                c.setSpecialMoves(new Move[]{legacy});
                needsMigration = true;
            } else {
                Gdx.app.log("CharacterLoader",
                        "旧形式 specialMove のコマンド '" + legacyCmd + "' は未知のため移行をスキップ: " + src);
            }
        }
        if (needsMigration) {
            Gdx.app.log("CharacterLoader", "旧形式 JSON を新形式配列へ自動移行しました: " + src);
        }
    }

    /** 必須フィールド・値域を検証する。 */
    private static void validate(Character c, String src) {
        requireText(c.getId(), "id", src);
        requireText(c.getName(), "name", src);
        requirePositive(c.getHp(), "hp", src);
        requirePositive(c.getWalkSpeed(), "walkSpeed", src);
        requirePositive(c.getJumpPower(), "jumpPower", src);
        requirePositive(c.getWidth(), "width", src);
        requirePositive(c.getHeight(), "height", src);
        requireOptionalRgb(c.getColor(), "color", src);
        requireOptionalSprite(c.getSprite(), "sprite", src);

        Move[] normals = c.getNormalMoves();
        if (normals == null || normals.length == 0) {
            throw new SchemaException("normalMoves は 1 件以上が必要 (" + src + ")");
        }
        for (int i = 0; i < normals.length; i++) {
            validateNormalMove(normals[i], "normalMoves[" + i + "]", src);
        }

        Move[] specials = c.getSpecialMoves();
        if (specials != null) {
            for (int i = 0; i < specials.length; i++) {
                validateSpecialMove(specials[i], "specialMoves[" + i + "]", src);
            }
        }
    }

    /** 通常技の検証（button は "light"/"medium"/"heavy" に限定）。 */
    private static void validateNormalMove(Move m, String field, String src) {
        if (m == null) {
            throw new SchemaException(field + " が null (" + src + ")");
        }
        requireText(m.getId(), field + ".id", src);
        requireValidButton(m.getButton(), field + ".button", src);
        requireNonNegative(m.getDamage(), field + ".damage", src);
        requireNonNegative(m.getStartup(), field + ".startup", src);
        requireNonNegative(m.getActive(), field + ".active", src);
        requireNonNegative(m.getRecovery(), field + ".recovery", src);
        if (m.getTotalFrames() <= 0) {
            throw new SchemaException(field + " の startup+active+recovery は 1 以上が必要 (" + src + ")");
        }
        requirePositive(m.getHitboxWidth(), field + ".hitboxWidth", src);
        requirePositive(m.getHitboxHeight(), field + ".hitboxHeight", src);
        requireValidGuardHeight(m.getGuardHeightToken(), field + ".guardHeight", src);
    }

    /** 必殺技の検証（command は実装済みコマンド名に限定・飛び道具時は projectileSpeed 必須）。 */
    private static void validateSpecialMove(Move m, String field, String src) {
        if (m == null) {
            throw new SchemaException(field + " が null (" + src + ")");
        }
        requireText(m.getId(), field + ".id", src);
        requireValidCommand(m.getCommand(), field + ".command", src);
        requireNonNegative(m.getDamage(), field + ".damage", src);
        requireNonNegative(m.getStartup(), field + ".startup", src);
        requireNonNegative(m.getActive(), field + ".active", src);
        requireNonNegative(m.getRecovery(), field + ".recovery", src);
        if (m.getTotalFrames() <= 0) {
            throw new SchemaException(field + " の startup+active+recovery は 1 以上が必要 (" + src + ")");
        }
        requirePositive(m.getHitboxWidth(), field + ".hitboxWidth", src);
        requirePositive(m.getHitboxHeight(), field + ".hitboxHeight", src);
        if (m.isProjectile()) {
            requirePositive(m.getProjectileSpeed(), field + ".projectileSpeed", src);
        }
        requireValidGuardHeight(m.getGuardHeightToken(), field + ".guardHeight", src);
    }

    /** ボタン種別が許可値（"light"/"medium"/"heavy"）であることを検証する。 */
    private static void requireValidButton(String value, String field, String src) {
        if (value == null || value.trim().isEmpty()) {
            throw new SchemaException("必須フィールド欠落 / 空: " + field + " (" + src + ")");
        }
        if (!VALID_BUTTONS.contains(value.trim().toLowerCase())) {
            throw new SchemaException(
                    field + " の値 \"" + value + "\" は不正です。許可値: light / medium / heavy (" + src + ")");
        }
    }

    /**
     * ガード高さ属性が許可値（{@link GuardHeight}）であることを検証する（Task 33）。
     * 生トークン（{@link Move#getGuardHeightToken()}）を {@link GuardHeight#fromToken(String)} で解釈し、
     * {@code null} / 空（未指定 → 既定の中段）は許可、未知値（例 "high"）のみ弾く。
     */
    private static void requireValidGuardHeight(String token, String field, String src) {
        boolean unknown = token != null && !token.trim().isEmpty() && GuardHeight.fromToken(token) == null;
        if (unknown) {
            throw new SchemaException(
                    field + " の値 \"" + token + "\" は不正です。許可値: overhead / mid / low (" + src + ")");
        }
    }

    /** コマンド名が実装済みコマンド（{@link #VALID_COMMANDS}）に含まれることを検証する。 */
    private static void requireValidCommand(String value, String field, String src) {
        if (value == null || value.trim().isEmpty()) {
            throw new SchemaException("必須フィールド欠落 / 空: " + field + " (" + src + ")");
        }
        if (!VALID_COMMANDS.contains(value.trim().toUpperCase())) {
            throw new SchemaException(
                    field + " の値 \"" + value + "\" は未知のコマンドです。許可値: "
                            + VALID_COMMANDS + " (" + src + ")");
        }
    }

    /**
     * スプライト定義を検証する（Task 34・任意フィールド）。{@code null}（未指定）は許可。
     * 指定時は path 非空・frameWidth/frameHeight が正・stateRows[].state 非空・row 非負を要求する。
     * 実在チェック（PNG が存在するか）は描画側（{@code SpriteLibrary}）に委ね、欠落時は矩形へフォールバックする。
     */
    private static void requireOptionalSprite(com.phantomnexus.shared.types.Sprite sprite, String field, String src) {
        if (sprite == null) {
            return;
        }
        requireText(sprite.getPath(), field + ".path", src);
        requirePositive(sprite.getFrameWidth(), field + ".frameWidth", src);
        requirePositive(sprite.getFrameHeight(), field + ".frameHeight", src);
        com.phantomnexus.shared.types.SpriteStateRow[] rows = sprite.getStateRows();
        if (rows != null) {
            // 正規化（trim + toLowerCase）後の state 重複を検出する。SpriteLibrary は state を
            // 正規化して Map へ入れるため、重複があると静かに上書きされ意図しない行マッピングになる。
            Set<String> seenStates = new HashSet<>();
            for (int i = 0; i < rows.length; i++) {
                com.phantomnexus.shared.types.SpriteStateRow r = rows[i];
                if (r == null) {
                    throw new SchemaException(field + ".stateRows[" + i + "] が null (" + src + ")");
                }
                requireText(r.getState(), field + ".stateRows[" + i + "].state", src);
                if (!seenStates.add(r.getState().trim().toLowerCase())) {
                    throw new SchemaException(
                            field + ".stateRows[" + i + "].state が重複しています: \""
                                    + r.getState() + "\" (" + src + ")");
                }
                if (r.getRow() < 0) {
                    throw new SchemaException(
                            field + ".stateRows[" + i + "].row は 0 以上が必要 = " + r.getRow() + " (" + src + ")");
                }
            }
        }
    }

    private static void requireOptionalRgb(float[] color, String field, String src) {
        if (color == null) {
            return;
        }
        if (color.length < 3) {
            throw new SchemaException("色は長さ 3 の RGB 配列が必要: " + field + " (" + src + ")");
        }
        for (int i = 0; i < 3; i++) {
            if (color[i] < 0f || color[i] > 1f) {
                throw new SchemaException("色成分は 0..1 の範囲が必要: " + field + "[" + i + "] = " + color[i] + " (" + src + ")");
            }
        }
    }

    private static void requireText(String value, String field, String src) {
        if (value == null || value.trim().isEmpty()) {
            throw new SchemaException("必須フィールド欠落 / 空: " + field + " (" + src + ")");
        }
    }

    private static void requirePositive(float value, String field, String src) {
        if (!(value > 0f)) {
            throw new SchemaException("正の値が必要: " + field + " = " + value + " (" + src + ")");
        }
    }

    private static void requireNonNegative(int value, String field, String src) {
        if (value < 0) {
            throw new SchemaException("負値は不可: " + field + " = " + value + " (" + src + ")");
        }
    }
}
