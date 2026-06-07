package com.phantomnexus.shared.types;

/**
 * 攻撃判定の矩形（AABB, ワールド座標）。Task 12: 当たり判定処理。
 *
 * <p>技の {@code active} 区間中のみ生成される攻撃判定。{@link Move} の相対 hitbox 定義（前方・足元基準）と
 * ファイターの位置・向きから {@code GameRuntime/Battle} の衝突処理が毎フレーム生成する。相手の
 * {@link Hurtbox} と重なるとヒット（[docs/BattleSystem.md](../../../../../../docs/BattleSystem.md)）。
 * 与ダメージ {@code damage} を保持し、Task 13（ダメージ処理）で参照する。
 */
public class Hitbox {

    private float x;
    private float y;
    private float width;
    private float height;
    private int damage;

    /** JSON / リフレクション用の無引数コンストラクタ。 */
    public Hitbox() {
    }

    public Hitbox(float x, float y, float width, float height, int damage) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.damage = damage;
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

    /** 命中時に相手へ与えるダメージ（Task 13 で使用）。 */
    public int getDamage() {
        return damage;
    }
}
