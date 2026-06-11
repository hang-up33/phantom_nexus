package com.phantomnexus.shared.types;

/**
 * キャラクターのスプライト（描画用画像）定義（Task 34: スプライト描画）。
 *
 * <p>外部 JSON（{@code Assets/Characters/<id>.json}）の任意フィールド {@code sprite} に対応する POJO。
 * スプライトシート（格子状に並んだフレーム画像の 1 枚 PNG）の場所とフレーム寸法、そして
 * 「アニメーション状態 → シート上の行」の対応を保持する。実際の {@code Texture} 読み込み・フレーム
 * 切り出しは描画側（{@code GameRuntime/Rendering} の {@code SpriteLibrary}）が行い、本クラスは
 * <strong>データ（パスとレイアウト）の単一の真実</strong>のみを担う（CLAUDE.md「データモデルの単一の真実」）。
 *
 * <p>シートは {@code frameWidth}×{@code frameHeight} の等間隔グリッドとして解釈する。各
 * アニメーション状態は {@link #getStateRows()} で行番号にマップされ（未マップは行 0 = 待機へ
 * フォールバック）、フレーム（列）番号は実行時のアニメーション進行から決まる。
 *
 * <p>{@code sprite} フィールドは任意で、未指定の旧 JSON は従来どおりプレースホルダ矩形で描画される
 * （後方互換）。{@code path} は {@code Assets/} ルート（クラスパス）からの相対パス
 * （例：{@code "Characters/fighter001.png"}）。
 *
 * @see SpriteStateRow
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 */
public class Sprite {

    /** スプライトシート PNG のパス（{@code Assets/} ルート＝クラスパス相対。例 {@code "Characters/fighter001.png"}）。 */
    private String path;
    /** 1 フレーム（セル）の横幅（px）。シートはこの幅の等間隔グリッドとして切り出される。 */
    private int frameWidth;
    /** 1 フレーム（セル）の高さ（px）。 */
    private int frameHeight;
    /** アニメーション状態 → シート上の行番号の対応（省略可。未マップ状態は行 0 へフォールバック）。 */
    private SpriteStateRow[] stateRows;

    /** JSON デシリアライズ用の無引数コンストラクタ。 */
    public Sprite() {
    }

    /** スプライトシート PNG のパス（クラスパス相対）。 */
    public String getPath() {
        return path;
    }

    /** 1 フレームの横幅（px）。 */
    public int getFrameWidth() {
        return frameWidth;
    }

    /** 1 フレームの高さ（px）。 */
    public int getFrameHeight() {
        return frameHeight;
    }

    /** アニメーション状態 → 行番号の対応（null / 空 = すべて行 0）。 */
    public SpriteStateRow[] getStateRows() {
        return stateRows;
    }
}
