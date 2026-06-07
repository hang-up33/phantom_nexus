package com.phantomnexus.runtime.battle;

/**
 * 攻撃の進行区間（Task 11: 攻撃処理）。
 *
 * <p>攻撃は {@code STARTUP → ACTIVE → RECOVERY} と進み、終了すると {@code NONE}（非攻撃）へ戻る。
 * 攻撃判定（hitbox）が有効なのは {@link #ACTIVE} の間のみ（[docs/BattleSystem.md](../../../../../../docs/BattleSystem.md)）。
 */
public enum AttackPhase {
    /** 非攻撃（通常状態）。 */
    NONE,
    /** 発生：技を出してから攻撃判定が出るまで。 */
    STARTUP,
    /** 持続：hitbox が有効（この間に相手 hurtbox と重なるとヒット。当たり判定は Task 12）。 */
    ACTIVE,
    /** 硬直：技後の行動不能区間。 */
    RECOVERY;

    /** 攻撃中（NONE 以外）か。 */
    public boolean isAttacking() {
        return this != NONE;
    }
}
