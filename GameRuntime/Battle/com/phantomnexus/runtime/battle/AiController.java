package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.types.AttackButton;

/**
 * 簡易 AI（Task 21 → Task 37 で読み合い反応を追加）。1 体のファイターを状態ベースで操作する。
 *
 * <p>人間の {@code PlayerInput} の代わりに、相手との距離 <b>＋ 相手の現在状態</b>から行動を決めて
 * {@link Fighter#update(int, boolean, AttackButton, boolean, boolean)} を呼ぶ（入力源の差し替え）。
 * Task 21 の素朴な「近づいて、間合いに入ったら攻撃」に、Task 37 で 2 つの反応を足した：
 * <ul>
 *   <li><b>ガード反応</b>：相手が打撃中（投げ以外）で間合い内なら、後退方向を保持して<b>ガード</b>する（chip のみで凌ぐ）。</li>
 *   <li><b>投げ崩し</b>：相手が<b>ガード中</b>で近接なら、ガード不能の<b>投げ</b>で崩す（打撃＝ガードされる相手への択）。</li>
 * </ul>
 * 判断は相手の観測可能な状態（{@link Fighter#isAttacking()} / {@link Fighter#isThrowing()} /
 * {@link Fighter#isGuarding()}）のみに基づき<b>乱数を使わない</b>（決定的＝入力リプレイと両立）。
 * 投げ抜けの反応・ジャンプ・必殺技は将来拡張。
 *
 * <p>状態（クールダウン）を持つため 1 体につき 1 インスタンス。判定に用いる距離は中心間距離。
 */
public final class AiController {

    /** この距離（中心間, px）以下で通常攻撃を試みる。通常攻撃の届く間合いより少し内側。 */
    private static final float ATTACK_RANGE = 150f;
    /** この距離（中心間, px）以下で相手の打撃に反応してガードする。攻撃間合いより少し広く取り、被弾前に盾を構える。 */
    private static final float GUARD_RANGE = 200f;
    /** この距離（中心間, px）以下でガード中の相手を投げで崩す。掴みの届く近接に限定する。 */
    private static final float THROW_RANGE = 130f;
    /** 攻撃 / 投げ後に次の能動行動まで空けるフレーム数（連打防止）。 */
    private static final int ATTACK_COOLDOWN = 45;

    private int cooldown;

    /** ラウンド間リセット（クールダウンを消去して次ラウンド開始時の行動可否を初期化する）。 */
    public void reset() {
        cooldown = 0;
    }

    /**
     * 1 フレーム分、AI の判断で {@code self} を操作する。
     *
     * <p>優先順：<b>ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃</b>。相手の状態に反応する 2 つ（ガード / 投げ）を
     * 距離ベースの行動（接近 / 攻撃）より優先する。
     *
     * @param self     操作対象のファイター
     * @param opponent 相手（距離 / 状態判定の基準）
     */
    public void control(Fighter self, Fighter opponent) {
        if (cooldown > 0) {
            cooldown--;
        }
        float dx = opponent.getX() - self.getX();
        float distance = Math.abs(dx);
        int towardDir = dx >= 0 ? 1 : -1; // 相手の方向
        int backDir = -towardDir;          // 後退（ガード）方向
        boolean hasThrow = self.getDef().getThrowMove() != null;
        // 相手が打撃中か（投げはガード不能なのでガード反応の対象外）。
        boolean opponentStriking = opponent.isAttacking() && !opponent.isThrowing();

        int moveDir = 0;
        boolean attack = false;
        boolean throwReq = false;

        if (opponentStriking && distance <= GUARD_RANGE && self.canStartAction()) {
            // ガード反応：相手の打撃に合わせて後退方向を保持し、ガードで chip に抑える。
            moveDir = backDir;
        } else if (opponent.isGuarding() && hasThrow && distance <= THROW_RANGE
                && cooldown == 0 && self.canStartAction()) {
            // 投げ崩し：ガード偏重の相手をガード不能の投げで崩す（打撃は防がれるため）。
            throwReq = true;
            cooldown = ATTACK_COOLDOWN;
        } else if (distance > ATTACK_RANGE) {
            // 間合いの外：相手へ接近する。
            moveDir = towardDir;
        } else if (cooldown == 0 && self.canStartAction()) {
            // 間合いの内：通常攻撃を出す（クールダウン明け・行動可能時のみ）。
            attack = true;
            cooldown = ATTACK_COOLDOWN;
        }
        self.update(moveDir, false, attack ? AttackButton.LIGHT : null, false, throwReq);
    }
}
