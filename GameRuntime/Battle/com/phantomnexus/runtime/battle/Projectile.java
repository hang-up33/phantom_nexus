package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Move;

/**
 * 飛び道具（必殺技の弾）。Task 20: 必殺技ステート。
 *
 * <p>必殺技（{@link Move#isProjectile()}）の発動で発射され、前方へ等速で進む。相手の hurtbox に当たると
 * ダメージ＋のけぞりを与えて消滅する（命中処理は Core）。画面外に出たら寿命切れで消滅する。
 * 1 つの弾は 1 回ヒットしたら消える（多段しない）。
 */
public final class Projectile {

    private float x;            // 中心 X
    private final float y;      // 下端 Y（足元からの相対は発射時に解決済み）
    private final float vx;     // 横速度（px/frame, 進行方向）
    private final float width;
    private final float height;
    private final int damage;
    private final Fighter owner; // 発射者（自分には当たらない）
    private boolean alive = true;

    public Projectile(float x, float y, float vx, float width, float height, int damage, Fighter owner) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.width = width;
        this.height = height;
        this.damage = damage;
        this.owner = owner;
    }

    /** 1 フレーム進める。画面外に出たら消滅する。 */
    public void update() {
        x += vx;
        if (x + width / 2f < 0f || x - width / 2f > GameConstants.WORLD_WIDTH) {
            alive = false;
        }
    }

    /** 現在の判定矩形（ワールド AABB）。所有者ダメージを内包。 */
    public Hitbox hitbox() {
        return new Hitbox(x - width / 2f, y, width, height, damage);
    }

    public boolean isAlive() {
        return alive;
    }

    /** 命中などで消滅させる。 */
    public void kill() {
        alive = false;
    }

    public Fighter getOwner() {
        return owner;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public int getDamage() {
        return damage;
    }
}
