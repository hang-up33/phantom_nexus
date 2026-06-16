package com.phantomnexus.shared.types;

/**
 * ステージ背景の 1 レイヤー（多層パララックス背景の構成要素）。Task 151: ステージ背景レイヤーシステム。
 *
 * <p>スマブラ流の「奥行きのある多層背景」と SF6 流の「テーマ性のある情景」を、JSON だけで足せるよう
 * データ化したもの。{@link Stage#getLayers()} に遠景→近景の順で並べ、描画側（{@code GameRenderer}）が
 * 空グラデーションと地面の間に奥から順に描く。形状（{@link #getShape()}）はシルエットの種類で、
 * 高さ/数/色を変えてテーマ（山・都市・神殿・水平線）を表現する。すべて**決定的**に描く（乱数なし）。
 *
 * <p>任意フィールドはフィールド初期化子で既定値を持つため、欠落キーは安全な既定になる（後方互換）。
 * LibGDX {@code Json} がリフレクションで設定できるよう非 final・無引数コンストラクタを備える。
 */
public class StageLayer {

    /** シルエットの種類（小文字トークン）。未対応の値は描画側で無視（後方互換）。 */
    private String shape = "band";
    /** レイヤー色 RGB（0..1）。大気遠近のため奥のレイヤーほど空色に近づけ淡くするとよい。 */
    private float[] color;
    /** シルエットの下端 Y（ワールド座標）。既定は地平線（地面の高さ）相当。 */
    private float baseY = 120f;
    /** シルエットの高さ（px）。 */
    private float height = 120f;
    /** 要素数（buildings の棟数・peaks の山数・pillars の柱数など）。 */
    private int count = 8;
    /** 不透明度（0..1）。1 未満で奥のレイヤーへ溶け込ませる。 */
    private float alpha = 1f;
    /** 水平方向のゆっくりした自動ドリフト量（px/描画フレーム）。0 で静止。雲・もや等の演出用。 */
    private float drift = 0f;
    /**
     * 前景フラグ（Task 158）。true ならキャラクターより手前（前景）に描き奥行きを出す。
     * 既定 false＝従来どおり背景（空と地面の間）。前景の暗いボケ柱/草木で被写界深度を演出する用途。
     */
    private boolean front = false;

    /** JSON / リフレクション用の無引数コンストラクタ。 */
    public StageLayer() {
    }

    /** シルエット種類（null/空 → "band"・正規化済み小文字）。 */
    public String getShape() {
        if (shape == null || shape.trim().isEmpty()) {
            return "band";
        }
        return shape.trim().toLowerCase();
    }

    /** レイヤー色 RGB（未指定なら null＝描画側でスキップ）。 */
    public float[] getColor() {
        return color;
    }

    /** シルエット下端 Y。 */
    public float getBaseY() {
        return baseY;
    }

    /** シルエット高さ（負値は 0 に丸める）。 */
    public float getHeight() {
        return Math.max(0f, height);
    }

    /** 要素数（最低 1）。 */
    public int getCount() {
        return Math.max(1, count);
    }

    /** 不透明度（0..1 にクランプ）。 */
    public float getAlpha() {
        return Math.max(0f, Math.min(1f, alpha));
    }

    /** 水平ドリフト量（px/描画フレーム）。 */
    public float getDrift() {
        return drift;
    }

    /** 前景フラグ（true＝キャラより手前に描く・Task 158）。既定 false＝背景。 */
    public boolean isFront() {
        return front;
    }
}
