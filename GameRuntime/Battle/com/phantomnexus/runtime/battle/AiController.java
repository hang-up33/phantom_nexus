package com.phantomnexus.runtime.battle;

/**
 * 簡易 AI（Task 21）。1 体のファイターを状態ベースで操作する。
 *
 * <p>人間の {@code PlayerInput} の代わりに、相手との距離から行動（接近 / 通常攻撃）を決めて
 * {@link Fighter#update(int, boolean, boolean)} を呼ぶ（入力源の差し替え）。MVP の方針は素朴な
 * 「近づいて、間合いに入ったら攻撃」。攻撃後はクールダウンを置いて連打を防ぐ。ジャンプ・必殺技・
 * ガードは将来拡張（第一設計書「MVP は高度な AI をやらない」）。
 *
 * <p>状態（クールダウン）を持つため 1 体につき 1 インスタンス。判定に用いる距離は中心間距離。
 */
public final class AiController {

    /** この距離（中心間, px）以下で攻撃を試みる。通常攻撃の届く間合いより少し内側。 */
    private static final float ATTACK_RANGE = 150f;
    /** 攻撃後に次の攻撃まで空けるフレーム数（連打防止）。 */
    private static final int ATTACK_COOLDOWN = 45;

    private int cooldown;

    /**
     * 1 フレーム分、AI の判断で {@code self} を操作する。
     *
     * @param self     操作対象のファイター
     * @param opponent 相手（接近 / 間合い判定の基準）
     */
    public void control(Fighter self, Fighter opponent) {
        if (cooldown > 0) {
            cooldown--;
        }
        float dx = opponent.getX() - self.getX();
        float distance = Math.abs(dx);

        int moveDir = 0;
        boolean attack = false;
        if (distance > ATTACK_RANGE) {
            // 間合いの外：相手へ接近する。
            moveDir = dx >= 0 ? 1 : -1;
        } else if (cooldown == 0 && self.canStartAction()) {
            // 間合いの内：攻撃を出す（クールダウン明け・行動可能時のみ）。
            attack = true;
            cooldown = ATTACK_COOLDOWN;
        }
        self.update(moveDir, false, attack ? "light" : null);
    }
}
