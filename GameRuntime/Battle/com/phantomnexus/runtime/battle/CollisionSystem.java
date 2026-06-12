package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Hurtbox;
import com.phantomnexus.shared.types.Move;
import com.phantomnexus.shared.types.PushBox;

/**
 * 当たり判定処理（Task 12: hit / hurt / push）。
 *
 * <p>3 種の矩形（AABB）を {@link Fighter} の実行時状態から毎フレーム生成して判定する：
 * <ul>
 *   <li><b>Hitbox</b>：攻撃判定。{@code active} 区間中のみ生成（{@link Move} の相対定義 + 位置 / 向き）。</li>
 *   <li><b>Hurtbox</b>：食らい判定。MVP はキャラ矩形。</li>
 *   <li><b>PushBox</b>：押し合い判定。MVP はキャラ矩形。重なりを左右に押し戻す。</li>
 * </ul>
 * 本クラスは状態を持たず、判定（{@link #isHitting}）と押し戻し（{@link #resolvePush}）の純関数群を提供する。
 * ダメージ適用・のけぞりは Task 13、デバッグ枠表示は Task 18 が本判定の結果を利用する。
 *
 * @see <a href="../../../../../../docs/BattleSystem.md">docs/BattleSystem.md</a>
 */
public final class CollisionSystem {

    private CollisionSystem() {
        // ユーティリティ（インスタンス化禁止）
    }

    /**
     * ファイターの食らい判定。しゃがみ中は高さを 1/3 に削減して弾や高攻撃をかわせるようにする（Task 25）。
     *
     * <p>1/3（≒80px for height=240）にすることで、既定の弾 hitboxOffsetY（最低 100px）を下回り、
     * しゃがみで飛び道具を実際に回避できる。
     */
    public static Hurtbox hurtbox(Fighter f) {
        Character d = f.getDef();
        float h = f.isCrouching() ? d.getHeight() / 3f : d.getHeight();
        return new Hurtbox(f.getX() - d.getWidth() / 2f, f.getY(), d.getWidth(), h);
    }

    /** ファイターの押し合い判定（MVP はキャラ矩形）。 */
    public static PushBox pushbox(Fighter f) {
        Character d = f.getDef();
        return new PushBox(f.getX() - d.getWidth() / 2f, f.getY(), d.getWidth(), d.getHeight());
    }

    /**
     * 現在 active な攻撃 hitbox（ワールド座標）。active でない / 技未定義のときは {@code null}。
     *
     * <p>技の相対 hitbox（前方の前面・足元基準）を向きに応じて左右反転し、ファイター位置へ移す。
     */
    public static Hitbox activeHitbox(Fighter f) {
        if (!f.isHitboxActive()) {
            return null;
        }
        Move m = f.getCurrentMove();
        // 飛び道具技は body 付随の hitbox を持たない（ダメージは Projectile が運ぶ。Task 20）。
        if (m == null || m.isProjectile()) {
            return null;
        }
        Character d = f.getDef();
        float front = f.isFacingRight()
                ? f.getX() + d.getWidth() / 2f
                : f.getX() - d.getWidth() / 2f;
        float x = f.isFacingRight()
                ? front + m.getHitboxOffsetX()
                : front - m.getHitboxOffsetX() - m.getHitboxWidth();
        // 下段（しゃがみ）攻撃は技定義の高い hitboxOffsetY を使わず脚部の低位に出す（Task 31）。
        float offsetY = f.isCrouchAttacking() ? GameConstants.LOW_ATTACK_HITBOX_OFFSET_Y : m.getHitboxOffsetY();
        float y = f.getY() + offsetY;
        // EX 打撃必殺技（Task 54）は与ダメージを EX_DAMAGE_MULTIPLIER 倍にする（飛び道具 EX のダメージ強化と対）。
        int damage = f.isExAttack()
                ? Math.round(m.getDamage() * GameConstants.EX_DAMAGE_MULTIPLIER)
                : m.getDamage();
        return new Hitbox(x, y, m.getHitboxWidth(), m.getHitboxHeight(), damage);
    }

    /** 2 つの AABB（左下原点・幅高さ）が重なるか。 */
    public static boolean overlaps(float ax, float ay, float aw, float ah,
                                   float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    /**
     * {@code attacker} の active hitbox が {@code defender} の hurtbox に重なるか。
     * 多段ヒット防止フラグ（{@link Fighter#hasAttackConnected()}）は呼び出し側で判断する
     * （本メソッドは純粋な幾何判定のみ）。
     */
    public static boolean isHitting(Fighter attacker, Fighter defender) {
        // 無敵フレーム中（リバーサル / 対空・Task 53）の相手は食らい判定を失うため当たらない。
        if (defender.isInvincible()) {
            return false;
        }
        Hitbox hb = activeHitbox(attacker);
        if (hb == null) {
            return false;
        }
        Hurtbox hu = hurtbox(defender);
        return overlaps(hb.getX(), hb.getY(), hb.getWidth(), hb.getHeight(),
                hu.getX(), hu.getY(), hu.getWidth(), hu.getHeight());
    }

    /** 飛び道具 {@code p} が {@code defender} の hurtbox に重なるか（Task 20）。 */
    public static boolean hits(Projectile p, Fighter defender) {
        // 無敵フレーム中（Task 53）は飛び道具も食らわない（無敵対空でリバーサルが弾を抜ける）。
        if (defender.isInvincible()) {
            return false;
        }
        Hitbox hb = p.hitbox();
        Hurtbox hu = hurtbox(defender);
        return overlaps(hb.getX(), hb.getY(), hb.getWidth(), hb.getHeight(),
                hu.getX(), hu.getY(), hu.getWidth(), hu.getHeight());
    }

    /**
     * 両者の pushbox が重なっていれば、横方向のめり込み量を等分して左右へ押し戻す。
     * 押し戻し後の画面端クランプは {@link Fighter#nudgeX(float)} 側で行う（端では片側に寄る）。
     */
    public static void resolvePush(Fighter a, Fighter b) {
        PushBox pa = pushbox(a);
        PushBox pb = pushbox(b);
        if (!overlaps(pa.getX(), pa.getY(), pa.getWidth(), pa.getHeight(),
                pb.getX(), pb.getY(), pb.getWidth(), pb.getHeight())) {
            return;
        }
        // 横方向のめり込み量（左にいる側の右端 − 右にいる側の左端）。
        boolean aIsLeft = a.getX() <= b.getX();
        float penetration = aIsLeft
                ? (pa.getX() + pa.getWidth()) - pb.getX()
                : (pb.getX() + pb.getWidth()) - pa.getX();
        if (penetration <= 0f) {
            return;
        }
        float half = penetration / 2f;
        int dir = aIsLeft ? 1 : -1; // a が左なら a を左へ、b を右へ
        a.nudgeX(-dir * half);
        b.nudgeX(dir * half);
    }
}
