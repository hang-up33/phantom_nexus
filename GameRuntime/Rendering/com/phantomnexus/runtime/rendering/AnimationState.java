package com.phantomnexus.runtime.rendering;

/**
 * キャラクターの視覚状態（Task 9: アニメーション管理）。
 *
 * <p>ファイターの実行時状態（接地 / 歩行 / 空中）から導出される描画上のアニメーション状態を表す。
 * 各状態はフレーム枚数・1 フレームの表示尺（tick 数）・ループ可否を持ち、{@link FighterAnimator} が
 * これらを使って経過 tick から現在フレーム番号を算出する。
 *
 * <p>本プロジェクトは 60fps 固定ステップ（{@code Shared/Constants}）を基準とし、1 回の更新 = 1 tick
 * として扱う（delta 時間に依存せずヘッドレススクショでも決定的）。MVP ではスプライト素材が未導入のため、
 * フレーム枚数・尺は仮の値であり、{@link GameRenderer} がプレースホルダ矩形上で可視化する。Task 15/16 で
 * キャラ JSON にスプライトシート / アニメ定義が入った段階で、各状態へ実フレーム（{@code TextureRegion}）を
 * 割り当て、ここで定義する frameCount / ticksPerFrame を JSON 由来の値に差し替える。
 */
public enum AnimationState {

    /** 待機。微小な「呼吸」ループ。 */
    IDLE(4, 12, true),
    /** 歩行。左右移動中の小刻みなループ。 */
    WALK(4, 6, true),
    /** 空中（ジャンプ / 落下）。滞空中の単一ポーズ（位置自体は物理で変化する）。 */
    JUMP(1, 1, false),
    /** 攻撃。区間（startup/active/recovery）の進行は {@code Fighter.attackPhase} 側が持つ単一状態。 */
    ATTACK(1, 1, false),
    /** のけぞり（hitstun）。被弾中の単一ポーズ（残フレームは {@code Fighter.hitstunFrames} 側が持つ）。 */
    HITSTUN(1, 1, false),
    /** しゃがみ（Task 25）。接地中に DOWN を押し続けている低姿勢ループ。 */
    CROUCH(2, 10, true),
    /** しゃがみ攻撃（Task 28）。低姿勢を維持したまま攻撃する単一ポーズ。 */
    CROUCH_ATTACK(1, 1, false),
    /** ガード中（後退方向保持・接地）。単一ポーズ（Task 27）。 */
    GUARD(1, 1, false);

    private final int frameCount;
    private final int ticksPerFrame;
    private final boolean looping;

    AnimationState(int frameCount, int ticksPerFrame, boolean looping) {
        this.frameCount = frameCount;
        this.ticksPerFrame = ticksPerFrame;
        this.looping = looping;
    }

    /** この状態の総フレーム枚数。 */
    public int frameCount() {
        return frameCount;
    }

    /** 1 フレームを表示する tick 数（60fps 基準の保持尺）。 */
    public int ticksPerFrame() {
        return ticksPerFrame;
    }

    /** ループ再生するか（true: 末尾で先頭へ戻る / false: 末尾フレームで保持）。 */
    public boolean isLooping() {
        return looping;
    }

    /**
     * 状態開始からの経過 tick から、現在のフレーム番号（0 始まり）を求める。
     *
     * <p>ループ状態は剰余で巻き戻し、非ループ状態は末尾フレームで頭打ちにする。
     *
     * @param ticksInState 当該状態に入ってからの経過 tick（0 以上）
     * @return 現在のフレーム番号（0 〜 frameCount-1）
     */
    public int frameAt(int ticksInState) {
        if (frameCount <= 1 || ticksPerFrame <= 0) {
            return 0;
        }
        int raw = ticksInState / ticksPerFrame;
        return looping ? raw % frameCount : Math.min(raw, frameCount - 1);
    }

    /** HUD / デバッグ表示用の小文字ラベル（例：{@code "idle"}）。 */
    public String label() {
        return name().toLowerCase();
    }
}
