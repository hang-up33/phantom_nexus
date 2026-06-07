package com.phantomnexus.shared.types;

/**
 * 押し合い判定の矩形（AABB, ワールド座標）。Task 12: 当たり判定処理。
 *
 * <p>キャラ同士のめり込みを解消するための体当たり判定。両者の {@link PushBox} が重なったら左右へ
 * 押し戻す（[docs/BattleSystem.md](../../../../../../docs/BattleSystem.md)）。MVP ではキャラ矩形を
 * そのまま pushbox とする。将来はキャラ JSON で個別定義可能にする。
 */
public class PushBox {

    private float x;
    private float y;
    private float width;
    private float height;

    /** JSON / リフレクション用の無引数コンストラクタ。 */
    public PushBox() {
    }

    public PushBox(float x, float y, float width, float height) {
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
