package com.phantomnexus.shared.schema;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.phantomnexus.shared.types.Stage;

/**
 * ステージ JSON のローダ（Task 17）。{@code Assets/Stages/<id>.json} を LibGDX {@link Json} で
 * {@link Stage} POJO へデシリアライズし、必須フィールド（id/name・色配列）を検証する。
 *
 * <p>{@link CharacterLoader} と同方針：classpath から読み、未知フィールドは無視（前方互換）、
 * 不正時はどのファイル / フィールドが原因かを含む {@link SchemaException} を投げる。
 */
public final class StageLoader {

    private StageLoader() {
        // ユーティリティ（インスタンス化禁止）
    }

    private static final String BASE_PATH = "Stages/";

    /** 指定 ID のステージを {@code Stages/<id>.json} から読み込み、検証して返す。 */
    public static Stage load(String id) {
        String path = BASE_PATH + id + ".json";
        FileHandle file = Gdx.files.classpath(path);
        if (!file.exists()) {
            throw new SchemaException("ステージ JSON が見つかりません: " + path);
        }
        Stage stage;
        try {
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            stage = json.fromJson(Stage.class, file);
        } catch (RuntimeException e) {
            throw new SchemaException("ステージ JSON の解析に失敗: " + path + " (" + e.getMessage() + ")", e);
        }
        if (stage == null) {
            throw new SchemaException("ステージ JSON が空です: " + path);
        }
        validate(stage, path);
        Gdx.app.log("StageLoader", "読み込み成功: " + path + " (" + stage.getName() + ")");
        return stage;
    }

    private static void validate(Stage s, String src) {
        requireText(s.getId(), "id", src);
        requireText(s.getName(), "name", src);
        requireRgb(s.getSkyTop(), "skyTop", src);
        requireRgb(s.getSkyBottom(), "skyBottom", src);
        requireRgb(s.getGroundColor(), "groundColor", src);
    }

    private static void requireText(String value, String field, String src) {
        if (value == null || value.trim().isEmpty()) {
            throw new SchemaException("必須フィールド欠落 / 空: " + field + " (" + src + ")");
        }
    }

    /** RGB 色配列（長さ 3・各 0..1）であることを検証する。 */
    private static void requireRgb(float[] color, String field, String src) {
        if (color == null || color.length < 3) {
            throw new SchemaException("色は長さ 3 の RGB 配列が必要: " + field + " (" + src + ")");
        }
        for (int i = 0; i < 3; i++) {
            if (color[i] < 0f || color[i] > 1f) {
                throw new SchemaException("色成分は 0..1 の範囲が必要: " + field + "[" + i + "] = " + color[i] + " (" + src + ")");
            }
        }
    }
}
