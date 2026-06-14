package com.phantomnexus.runtime.battle;

/**
 * 着地の砂煙 1 件分の実行時状態（ジャンプ / 浮かせからの着地で足元に出す土埃の演出）（Task 131）。
 *
 * <p>ファイターが滞空→接地へ遷移したフレームに足元（接地 Y）へ生成し、毎フレーム {@link #update()} で
 * 経過フレームを数える。描画側（{@code GameRenderer}）が経過フレームから広がり・上昇・フェードを導出して
 * 複数の小さな土埃を描く。{@link HitSpark} / {@link DamagePopup} / {@link Projectile} と同じく状態（原点・
 * 寿命）だけを持つ POJO とし、描画リソースや描画ロジックは持たない。
 *
 * <p>純粋な視覚演出であり HP 計算や位置・当たり判定とは独立（戦闘結果に影響しない・乱数なし＝決定的）。
 * 寿命を過ぎたら {@code PhantomNexusGame} 側が一覧から取り除く。
 */
public class LandingDust {

    private final float originX;
    private final float originY;
    private final int lifespan;
    private int age;

    /**
     * @param originX  着地した足元の中心 X（ワールド座標）。
     * @param originY  着地面の Y（ワールド座標。接地時の足元 Y）。
     * @param lifespan 表示フレーム数（{@code GameConstants.LANDING_DUST_FRAMES}）。
     */
    public LandingDust(float originX, float originY, int lifespan) {
        this.originX = originX;
        this.originY = originY;
        this.lifespan = lifespan;
    }

    /** 経過フレームを 1 進める。 */
    public void update() {
        age++;
    }

    /** 寿命を過ぎたか（一覧からの除去判定）。 */
    public boolean isExpired() {
        return age >= lifespan;
    }

    /** 着地した足元の中心 X（ワールド座標）。 */
    public float getOriginX() {
        return originX;
    }

    /** 着地面の Y（ワールド座標）。 */
    public float getOriginY() {
        return originY;
    }

    /** 生成からの経過フレーム数（0 始まり）。 */
    public int getAge() {
        return age;
    }

    /** 表示フレーム数（寿命）。 */
    public int getLifespan() {
        return lifespan;
    }
}
