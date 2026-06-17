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
    private StageLayer[] layers; // 背景の多層シルエット（任意・遠景→近景の順。Task 151）
    private String background;   // 全画面 1 枚絵の背景 PNG パス（任意・外部デザイン取り込み用）

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

    /** 背景の多層シルエット（任意・遠景→近景の順。未指定なら null＝従来どおり空＋地面のみ）。Task 151。 */
    public StageLayer[] getLayers() {
        return layers;
    }

    /**
     * 全画面 1 枚絵の背景 PNG パス（{@code Assets/} ルート＝クラスパス相対。例
     * {@code "Stages/stage011_bg.png"}）。外部デザインツール（ClaudeDesign 等）で作った
     * ステージアートをそのまま敷くための任意フィールド。
     *
     * <p>指定があり画像が読めれば、描画側（{@code GameRenderer}）は手続き的な空グラデ・多層シルエット・
     * 地面塗りの代わりにこの 1 枚絵を全画面に描く（{@code StageBackgroundLibrary}）。未指定（{@code null}）
     * や画像欠落・読み込み失敗時は従来どおり手続き背景へフォールバックする（後方互換）。空/地面色や
     * {@link #getLayers()} はフォールバック用に引き続き定義しておくとよい。
     *
     * @return 背景 PNG パス（クラスパス相対）。未指定なら {@code null}。
     */
    public String getBackground() {
        if (background == null || background.trim().isEmpty()) {
            return null;
        }
        return background.trim();
    }
}
