package com.phantomnexus.shared.types;

/**
 * 食らい判定の矩形（AABB, ワールド座標）。Task 12: 当たり判定処理。
 *
 * <p>相手の {@link Hitbox} と重なると被弾する判定。MVP ではキャラ矩形（{@code Character.width/height} を
 * ファイター位置に置いたもの）をそのまま hurtbox とする。将来はキャラ JSON で部位別に定義可能にする。
 */
public class Hurtbox {

    private float x;
    private float y;
    private float width;
    private float height;

    /** JSON / リフレクション用の無引数コンストラクタ。 */
    public Hurtbox() {
    }

    public Hurtbox(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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
}
