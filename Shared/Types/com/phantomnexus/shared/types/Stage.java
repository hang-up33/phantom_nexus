package com.phantomnexus.shared.types;

/**
 * ステージの静的定義（データの単一の真実）。Task 17: ステージ表示。
 *
 * <p>背景（空のグラデーション）と地面の色を持つ MVP ステージ。色は RGB（各 0.0〜1.0）の長さ 3 配列。
 * 外部 JSON（{@code Assets/Stages/<id>.json}）から読み込む（[docs/DataFormat.md](../../../../../../docs/DataFormat.md)）。
 * 地面の高さ（物理基準）は {@code Shared/Constants.GROUND_Y} を用い、本型は見た目のみを担う（MVP）。
 * LibGDX {@code Json} がリフレクションで設定できるよう非 final・無引数コンストラクタを備える。
 */
public class Stage {

    private String id;
    private String name;
    private float[] skyTop;      // 空の上端色 RGB（0..1）
    private float[] skyBottom;   // 空の下端色（地平線側）RGB
    private float[] groundColor; // 地面色 RGB

    /** JSON / リフレクション用の無引数コンストラクタ。 */
    public Stage() {
    }

    public Stage(String id, String name, float[] skyTop, float[] skyBottom, float[] groundColor) {
        this.id = id;
        this.name = name;
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
        this.groundColor = groundColor;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float[] getSkyTop() {
        return skyTop;
    }

    public float[] getSkyBottom() {
        return skyBottom;
    }

    public float[] getGroundColor() {
        return groundColor;
    }
}
