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
    private final boolean ex;    // EX 必殺技（メーター消費の強化版）か（描画の大型化・色変えに使用・Task 44）
    private boolean alive = true;

    // 飛び道具の軌跡（motion trail・Task 134）：直近の通過位置（中心 X）を上書き式に保持するリングバッファ。
    // 描画側（GameRenderer）が古い位置ほど薄く小さい尾を引いて速度感・残像感を出す純演出（Y は固定なので X だけ）。
    private static final int TRAIL_MAX = 5;
    private final float[] trailX = new float[TRAIL_MAX];
    private int trailSize; // 有効な軌跡点の数（0..TRAIL_MAX）
    private int trailHead; // 次に書き込む位置（リングバッファ）

    public Projectile(float x, float y, float vx, float width, float height, int damage, Fighter owner) {
        this(x, y, vx, width, height, damage, owner, false);
    }

    public Projectile(float x, float y, float vx, float width, float height, int damage, Fighter owner, boolean ex) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.width = width;
        this.height = height;
        this.damage = damage;
        this.owner = owner;
        this.ex = ex;
    }

    /** 1 フレーム進める。画面外に出たら消滅する。 */
    public void update() {
        // 移動前に現在位置を軌跡へ記録（描画側が尾として後ろに引く。Task 134）。決定的＝実位置のみ。
        trailX[trailHead] = x;
        trailHead = (trailHead + 1) % TRAIL_MAX;
        if (trailSize < TRAIL_MAX) {
            trailSize++;
        }
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

    /** EX 必殺技（メーター消費の強化版飛び道具）か（Task 44）。描画の大型化・色変えに使う。 */
    public boolean isEx() {
        return ex;
    }

    /** 軌跡（motion trail・Task 134）の有効点数（0..{@code TRAIL_MAX}）。描画側のループ範囲。 */
    public int getTrailSize() {
        return trailSize;
    }

    /**
     * 軌跡点の中心 X を「最古→最新」の順で返す（{@code i} は 0=最古〜{@code getTrailSize()-1}=最新）。
     * Y は固定（{@link #getY()}）なので X のみ保持する。描画側が尾の各点を薄→濃で描くのに使う（Task 134）。
     */
    public float getTrailX(int i) {
        int slot = (trailHead - trailSize + i + TRAIL_MAX) % TRAIL_MAX;
        return trailX[slot];
    }
}
