package com.phantomnexus.runtime.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * キーコンフィグ設定の永続化（Task 189）。
 *
 * <p>settings.json に P1/P2 のキー割当を保存・読込する。ファイルは LibGDX の local storage
 * （アプリ起動ディレクトリ）に置かれる。ファイルが存在しない場合はデフォルト割当のまま。
 *
 * <p>注意: InputAction は {@code GameRuntime/Input} にあるため、本クラスは {@code Shared} でなく
 * {@code GameRuntime/Core} に置く（Shared → GameRuntime の逆依存を避けるための例外）。
 */
public class SettingsManager {

    private static final String SETTINGS_PATH = "settings.json";

    /** 現在のキー割当を settings.json へ保存する。失敗してもゲームは続行する。 */
    public static void save(PlayerInput p1Input, PlayerInput p2Input) {
        try {
            SettingsData data = new SettingsData();
            for (InputAction action : InputAction.values()) {
                data.p1.put(action.name(), p1Input.getKey(action));
                data.p2.put(action.name(), p2Input.getKey(action));
            }
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            Gdx.files.local(SETTINGS_PATH).writeString(json.prettyPrint(data), false);
        } catch (Exception e) {
            Gdx.app.log("SettingsManager", "保存失敗: " + e.getMessage());
        }
    }

    /**
     * settings.json からキー割当を読み込んで p1Input/p2Input に適用する。
     * ファイルが存在しない場合や読込に失敗した場合は何もしない（デフォルト割当を維持）。
     */
    public static void load(PlayerInput p1Input, PlayerInput p2Input) {
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.local(SETTINGS_PATH);
            if (!fh.exists()) {
                return;
            }
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            SettingsData data = json.fromJson(SettingsData.class, fh.readString());
            if (data == null) {
                return;
            }
            applyBindings(data.p1, p1Input);
            applyBindings(data.p2, p2Input);
        } catch (Exception e) {
            Gdx.app.log("SettingsManager", "読込失敗: " + e.getMessage());
        }
    }

    private static void applyBindings(Map<String, Integer> map, PlayerInput input) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            try {
                InputAction action = InputAction.valueOf(entry.getKey());
                if (entry.getValue() != null && entry.getValue() >= 0) {
                    input.setBinding(action, entry.getValue());
                }
            } catch (IllegalArgumentException ignored) {
                // 未知のアクション名は無視（将来の後方互換）
            }
        }
    }

    /** 設定データの POJO（LibGDX Json が JSON ↔ Java を自動変換する）。 */
    public static class SettingsData {
        public Map<String, Integer> p1 = new LinkedHashMap<>();
        public Map<String, Integer> p2 = new LinkedHashMap<>();
    }
}
