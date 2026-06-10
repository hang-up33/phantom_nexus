package com.phantomnexus.shared.types;

/**
 * スプライトシート上の「アニメーション状態 → 行番号」対応 1 件（Task 34）。
 *
 * <p>{@link Sprite#getStateRows()} の要素。{@code state} はアニメーション状態の小文字ラベル
 * （例：{@code "idle"} / {@code "walk"} / {@code "crouch"}）で、描画側（{@code GameRuntime/Rendering}）の
 * {@code AnimationState.label()} と照合する。{@code row} はスプライトシート（{@code frameWidth}×
 * {@code frameHeight} のグリッド）における 0 始まりの行番号。フレーム（列）番号はアニメーション進行
 * （{@code FighterAnimator.getFrameIndex()}）から決まるため、ここでは行のみを持つ。
 *
 * <p>{@code Shared} から {@code GameRuntime/Rendering} の {@code AnimationState} へ依存しないよう、
 * 状態は列挙ではなく文字列で保持する（照合は描画側が小文字ラベルで行う）。未マップの状態は行 0 に
 * フォールバックする（{@link Sprite} 参照）。
 *
 * @see Sprite
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 */
public class SpriteStateRow {

    /** アニメーション状態の小文字ラベル（描画側 {@code AnimationState.label()} と照合）。 */
    private String state;
    /** スプライトシート上の行番号（0 始まり）。 */
    private int row;

    /** JSON デシリアライズ用の無引数コンストラクタ。 */
    public SpriteStateRow() {
    }

    public SpriteStateRow(String state, int row) {
        this.state = state;
        this.row = row;
    }

    /** アニメーション状態ラベル（小文字）。 */
    public String getState() {
        return state;
    }

    /** スプライトシート上の行番号（0 始まり）。 */
    public int getRow() {
        return row;
    }
}
