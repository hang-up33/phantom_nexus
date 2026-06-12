package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.types.AttackButton;

/**
 * 簡易 AI（Task 21 → Task 37 で読み合い反応を追加）。1 体のファイターを状態ベースで操作する。
 *
 * <p>人間の {@code PlayerInput} の代わりに、相手との距離 <b>＋ 相手の現在状態</b>から行動を決めて
 * {@link Fighter#update(int, boolean, AttackButton, boolean, boolean)} を呼ぶ（入力源の差し替え）。
 * Task 21 の素朴な「近づいて、間合いに入ったら攻撃」に、Task 37 で 2 つ・Task 51 で 1 つの反応を足した：
 * <ul>
 *   <li><b>投げ抜け反応</b>（Task 51）：相手が<b>投げ中</b>で近接なら、投げ抜け猶予窓をアームして掴みを<b>投げ抜け</b>る（ノーダメージ）。</li>
 *   <li><b>ガード反応</b>（Task 37）：相手が打撃中（投げ以外）で間合い内なら、後退方向を保持して<b>ガード</b>する（chip のみで凌ぐ）。</li>
 *   <li><b>投げ崩し</b>（Task 37）：相手が<b>ガード中</b>で近接なら、ガード不能の<b>投げ</b>で崩す（打撃＝ガードされる相手への択）。</li>
 * </ul>
 * 判断は相手の観測可能な状態（{@link Fighter#isAttacking()} / {@link Fighter#isThrowing()} /
 * {@link Fighter#isGuarding()}）のみに基づき<b>乱数を使わない</b>（決定的＝入力リプレイと両立）。これにより
 * 「打撃＝ガード／ガード＝投げで崩す／投げ＝投げ抜け」の三すくみが CPU 戦でも成立する。AI のジャンプ・必殺技・しゃがみ系は将来拡張。
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
    /**
     * この距離（中心間, px）以下で相手の投げに反応して投げ抜け窓をアームする（Task 51）。{@link #THROW_RANGE} より少し広く取り、
     * 掴みが成立する前（startup 中）に窓を立てて、active で掴まれた瞬間に抜けられるようにする。
     */
    private static final float THROW_TECH_RANGE = 160f;
    /** 攻撃 / 投げ後に次の能動行動まで空けるフレーム数（連打防止）。 */
    private static final int ATTACK_COOLDOWN = 45;
    /**
     * この距離（中心間, px）より遠ければ歩きでなくダッシュ（二度押し前ステップ・Task 49）で素早く間合いを詰める（Task 50）。
     * {@link #ATTACK_RANGE} までの接近のうち、遠距離はダッシュ・近距離は歩きと使い分ける。
     */
    private static final float DASH_APPROACH_RANGE = 260f;

    private int cooldown;
    /**
     * AI のダッシュ二度押しパターンの進行状態（Task 50）。Fighter のダッシュ検出は「同方向の押下エッジが受付窓内に 2 回」で
     * 成立するため、AI 側で 0=1 度目押下 → 1=ニュートラル（離す）→ 2=2 度目押下（発動）の 3 フレームを生成する。
     */
    private int dashTapStep;

    /** ラウンド間リセット（クールダウン・ダッシュ進行を消去して次ラウンド開始時の行動可否を初期化する）。 */
    public void reset() {
        cooldown = 0;
        dashTapStep = 0;
    }

    /**
     * 1 フレーム分、AI の判断で {@code self} を操作する。
     *
     * <p>優先順：<b>投げ抜け反応 ＞ ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃</b>。相手の状態に反応する 3 つ（投げ抜け / ガード / 投げ）を
     * 距離ベースの行動（接近 / 攻撃）より優先する。投げはガード不能なので、投げ反応（抜け）を最優先に置く。
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

        if (opponent.isThrowing() && distance <= THROW_TECH_RANGE
                && self.isGrounded() && self.canStartAction()) {
            // 投げ抜け反応（Task 51）：相手の掴み（ガード不能）に反応して投げ抜け窓をアームし、ニュートラルで抜けに専念する。
            // 掴みの startup 中から毎フレーム armThrowTech() し続けるので、active で掴まれた瞬間に canTechThrow() が成立して
            // 投げ抜け（相互に弾かれ・ノーダメージ）になる。空中は掴めない（Task 35）ので接地時のみ。乱数なし＝決定的。
            // 自分が攻撃硬直 / のけぞり中（canStartAction()==false）は窓を立てられず掴まれる＝硬直を投げで狩る択は残る。
            self.armThrowTech();
            dashTapStep = 0;
        } else if (opponentStriking && distance <= GUARD_RANGE && self.canStartAction()) {
            // ガード反応：相手の打撃に合わせて後退方向を保持し、ガードで chip に抑える。
            // ダッシュ接近中（dashFrames>0 で guarding が抑止される）に GUARD_RANGE 内で相手の打撃を検知したら、
            // 自分のダッシュをキャンセルしてガードを優先する（Task 50 / Codex 指摘）。ダッシュは AI 自身の選択なので
            // 防御のために中断してよく、これで「打撃にはガード」(Task 37) の保証が接近中も成立する。
            if (self.isDashing()) {
                self.cancelDash();
            }
            moveDir = backDir;
            dashTapStep = 0;
        } else if (opponent.isGuarding() && hasThrow && distance <= THROW_RANGE
                && cooldown == 0 && self.canStartAction()) {
            // 投げ崩し：ガード偏重の相手をガード不能の投げで崩す（打撃は防がれるため）。
            throwReq = true;
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        } else if (distance > DASH_APPROACH_RANGE && self.canStartAction()) {
            // 遠距離：ダッシュ（二度押し前ステップ）で素早く間合いを詰める（Task 50）。
            // Fighter のダッシュ検出（同方向押下エッジ×2 が受付窓内）に合わせ、押下→離す→押下の 3 フレームを生成する。
            if (self.isDashing()) {
                // 既にダッシュ発動中：方向を維持し（向き固定）、パターンを初期化して次の二度押しに備える。
                moveDir = towardDir;
                dashTapStep = 0;
            } else {
                switch (dashTapStep) {
                    case 0: // 1 度目の押下（エッジを立てる）
                        moveDir = towardDir;
                        dashTapStep = 1;
                        break;
                    case 1: // ニュートラル（一度離して次の押下をエッジにする）
                        moveDir = 0;
                        dashTapStep = 2;
                        break;
                    default: // 2 度目の押下（受付窓内ならダッシュ発動）
                        moveDir = towardDir;
                        dashTapStep = 0;
                        break;
                }
            }
        } else if (distance > ATTACK_RANGE) {
            // 間合いの外（ただしダッシュ距離より内）：歩いて接近する。
            moveDir = towardDir;
            dashTapStep = 0;
        } else if (cooldown == 0 && self.canStartAction()) {
            // 間合いの内：通常攻撃を出す（クールダウン明け・行動可能時のみ）。
            attack = true;
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        }
        self.update(moveDir, false, attack ? AttackButton.LIGHT : null, false, throwReq);
    }
}
