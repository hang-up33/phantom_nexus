package com.phantomnexus.shared.schema;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Move;

/**
 * キャラクター JSON のローダ（Task 16 / Task 24）。**データ I/O の単一の真実**（{@code Shared/Schema}）。
 *
 * <p>{@code Assets/Characters/<id>.json} を LibGDX 組込みの {@link Json}（追加ライブラリ無し）で
 * {@link Character} POJO へデシリアライズし、必須フィールドを検証する。Task 24 で技定義を
 * {@code normalMoves[]} / {@code specialMoves[]} の配列形式に拡張した。
 *
 * <p>{@code GameRuntime} / {@code Battle} は本ローダ経由でのみデータを取得し、直接 JSON を読まない
 * （CLAUDE.md「データモデルの単一の真実」）。
 */
public final class CharacterLoader {

    private CharacterLoader() {
        // ユーティリティ（インスタンス化禁止）
    }

    private static final String BASE_PATH = "Characters/";

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
        validate(character, path);
        Gdx.app.log("CharacterLoader", "読み込み成功: " + path + " (" + character.getName() + ")");
        return character;
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

    /** 通常技の検証（button 必須）。 */
    private static void validateNormalMove(Move m, String field, String src) {
        if (m == null) {
            throw new SchemaException(field + " が null (" + src + ")");
        }
        requireText(m.getId(), field + ".id", src);
        requireText(m.getButton(), field + ".button", src);
        requireNonNegative(m.getDamage(), field + ".damage", src);
        requireNonNegative(m.getStartup(), field + ".startup", src);
        requireNonNegative(m.getActive(), field + ".active", src);
        requireNonNegative(m.getRecovery(), field + ".recovery", src);
        if (m.getTotalFrames() <= 0) {
            throw new SchemaException(field + " の startup+active+recovery は 1 以上が必要 (" + src + ")");
        }
        requirePositive(m.getHitboxWidth(), field + ".hitboxWidth", src);
        requirePositive(m.getHitboxHeight(), field + ".hitboxHeight", src);
    }

    /** 必殺技の検証（command 必須・飛び道具時は projectileSpeed 必須）。 */
    private static void validateSpecialMove(Move m, String field, String src) {
        if (m == null) {
            throw new SchemaException(field + " が null (" + src + ")");
        }
        requireText(m.getId(), field + ".id", src);
        requireText(m.getCommand(), field + ".command", src);
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
