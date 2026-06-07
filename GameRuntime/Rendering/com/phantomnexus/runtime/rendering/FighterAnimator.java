package com.phantomnexus.runtime.rendering;

import com.phantomnexus.runtime.battle.Fighter;

/**
 * 1 体分のアニメーション状態機械（Task 9: アニメーション管理）。
 *
 * <p>{@link Fighter} の実行時状態（接地 / 歩行 / 空中）から {@link AnimationState} を毎フレーム導出し、
 * 状態継続中の経過 tick を数えて現在フレーム番号を提供する。状態が切り替わった瞬間に経過 tick を 0 へ
 * リセットすることで、アニメーションを先頭から再生する。1 回の {@link #update(Fighter)} 呼び出し = 1 tick
 * （60fps 固定ステップ基準）で進め、delta 時間に依存しない決定的な進行とする（ヘッドレススクショ向け）。
 *
 * <p>本クラスは描画リソース（テクスチャ）を直接持たず、状態 / フレーム番号という抽象のみを提供する。
 * Task 15/16 でスプライトシートが入った段階では、{@link GameRenderer} がここで得た状態 / フレーム番号で
 * {@code TextureRegion} を引く構成へ素直に拡張できる（本クラスの責務は不変）。
 */
public class FighterAnimator {

    private AnimationState state = AnimationState.IDLE;
    private int ticksInState = 0;

    /**
     * ファイターの現在状態からアニメーション状態を更新し、1 tick 進める。
     *
     * @param fighter 参照するファイター（読み取り専用に扱う）
     */
    public void update(Fighter fighter) {
        AnimationState desired = resolve(fighter);
        if (desired != state) {
            state = desired;
            ticksInState = 0;
        } else {
            ticksInState++;
        }
    }

    /** ファイターの実行時状態 → アニメーション状態の対応付け（攻撃 > 空中 > 歩行 > 待機の優先順）。 */
    private static AnimationState resolve(Fighter fighter) {
        if (fighter.isAttacking()) {
            return AnimationState.ATTACK;
        }
        if (!fighter.isGrounded()) {
            return AnimationState.JUMP;
        }
        if (fighter.isWalking()) {
            return AnimationState.WALK;
        }
        return AnimationState.IDLE;
    }

    /** 現在のアニメーション状態。 */
    public AnimationState getState() {
        return state;
    }

    /** 現在のフレーム番号（0 始まり）。 */
    public int getFrameIndex() {
        return state.frameAt(ticksInState);
    }

    /** 現在状態に入ってからの経過 tick。 */
    public int getTicksInState() {
        return ticksInState;
    }

    /**
     * プレースホルダ描画に「生き」を与えるための縦方向ボブ量（px）。
     *
     * <p>スプライト未導入の MVP では、矩形を上下に微小揺動させてアニメーション進行を可視化する。
     * 待機は三角波で「呼吸」、歩行は 1 フレームおきの上下動で「弾み」を表す。空中は物理で位置が
     * 変わるためボブを与えない。スプライト導入後（Task 15/16）はテクスチャ描画に置き換えるため不要になる。
     *
     * @return 矩形へ加算する Y オフセット（px、0 以上）
     */
    public float bobOffset() {
        switch (state) {
            case IDLE:
                // 0→1→2→1 の三角波（frameCount=4 前提）。約 3px の呼吸。
                int idx = getFrameIndex();
                int tri = idx <= state.frameCount() / 2 ? idx : state.frameCount() - idx;
                return tri * 1.5f;
            case WALK:
                // 1 フレームおきに 4px 持ち上げて歩行の弾みを表現。
                return (getFrameIndex() % 2 == 0) ? 0f : 4f;
            default:
                return 0f;
        }
    }
}
