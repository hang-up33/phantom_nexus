package com.phantomnexus.shared.schema;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Move;

/**
 * キャラクター JSON のローダ（Task 16）。**データ I/O の単一の真実**（`Shared/Schema`）。
 *
 * <p>{@code Assets/Characters/<id>.json} を LibGDX 組込みの {@link Json}（追加ライブラリ無し）で
 * {@link Character} POJO へデシリアライズし、必須フィールドを検証する。バリデーション失敗時は
 * どのファイル / フィールドが原因かを含む {@link SchemaException} を投げる（[docs/DataFormat.md](../../../../../../docs/DataFormat.md)）。
 *
 * <p>JSON は {@code processResources} によりクラスパス（{@code build/resources/main/Characters/...}）へ
 * 配置されるため {@link Gdx#files} の {@code classpath} で読む（パッケージ JAR でも解決できる）。
 * 前方互換のため未知フィールドは無視する（{@link Json#setIgnoreUnknownFields(boolean)}）。
 *
 * <p>{@code GameRuntime} / {@code Battle} は本ローダ経由でのみデータを取得し、直接 JSON を読まない
 * （CLAUDE.md「データモデルの単一の真実」）。
 */
public final class CharacterLoader {

    private CharacterLoader() {
        // ユーティリティ（インスタンス化禁止）
    }

    /** クラスパス上のキャラ JSON のベースパス（resources ルート = {@code Assets/} 配下）。 */
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
            json.setIgnoreUnknownFields(true); // 前方互換：未知フィールドは無視
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

    /** 必須フィールド・値域を検証する。原因フィールドを明示して {@link SchemaException} を投げる。 */
    private static void validate(Character c, String src) {
        requireText(c.getId(), "id", src);
        requireText(c.getName(), "name", src);
        requirePositive(c.getHp(), "hp", src);
        requirePositive(c.getWalkSpeed(), "walkSpeed", src);
        requirePositive(c.getJumpPower(), "jumpPower", src);
        requirePositive(c.getWidth(), "width", src);
        requirePositive(c.getHeight(), "height", src);
        Move atk = c.getNormalAttack();
        if (atk == null) {
            throw new SchemaException("必須フィールド欠落: normalAttack (" + src + ")");
        }
        requireText(atk.getId(), "normalAttack.id", src);
        requireNonNegative(atk.getDamage(), "normalAttack.damage", src);
        requireNonNegative(atk.getStartup(), "normalAttack.startup", src);
        requireNonNegative(atk.getActive(), "normalAttack.active", src);
        requireNonNegative(atk.getRecovery(), "normalAttack.recovery", src);
        if (atk.getTotalFrames() <= 0) {
            throw new SchemaException("normalAttack の startup+active+recovery は 1 以上が必要 (" + src + ")");
        }
        requirePositive(atk.getHitboxWidth(), "normalAttack.hitboxWidth", src);
        requirePositive(atk.getHitboxHeight(), "normalAttack.hitboxHeight", src);
        validateSpecial(c.getSpecialMove(), src);
    }

    /** 必殺技は任意（null 可）。設定されていればフレーム・hitbox・飛び道具速度を検証する。 */
    private static void validateSpecial(Move sp, String src) {
        if (sp == null) {
            return;
        }
        requireText(sp.getId(), "specialMove.id", src);
        requireNonNegative(sp.getDamage(), "specialMove.damage", src);
        requireNonNegative(sp.getStartup(), "specialMove.startup", src);
        requireNonNegative(sp.getActive(), "specialMove.active", src);
        requireNonNegative(sp.getRecovery(), "specialMove.recovery", src);
        if (sp.getTotalFrames() <= 0) {
            throw new SchemaException("specialMove の startup+active+recovery は 1 以上が必要 (" + src + ")");
        }
        requirePositive(sp.getHitboxWidth(), "specialMove.hitboxWidth", src);
        requirePositive(sp.getHitboxHeight(), "specialMove.hitboxHeight", src);
        if (sp.isProjectile()) {
            requirePositive(sp.getProjectileSpeed(), "specialMove.projectileSpeed", src);
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
