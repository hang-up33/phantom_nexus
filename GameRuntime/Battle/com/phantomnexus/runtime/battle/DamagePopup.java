package com.phantomnexus.runtime.battle;

/**
 * ダメージ数値ポップアップ 1 件分の実行時状態（被弾 / ガード時の与ダメージ量を可視化する演出）。
 *
 * <p>ヒット解決（{@code PhantomNexusGame.resolveHit}）や飛び道具命中時に、実際に減った HP 量を命中位置へ
 * 生成し、毎フレーム {@link #update()} で経過フレームを数える。描画側（{@code GameRenderer}）が経過フレーム
 * から上昇オフセットとフェードを導出して数字を浮かび上がらせる。{@link Projectile} と同じく、状態（量・種別・
 * 原点・寿命）だけを持つ POJO とし、描画リソースや描画ロジックは持たない。
 *
 * <p>純粋な視覚演出であり HP 計算とは独立（戦闘結果に影響しない）。種別（{@link Kind}）で通常ヒットと
 * ガード時の chip を色分けする。寿命を過ぎたら {@code PhantomNexusGame} 側が一覧から取り除く。
 */
public class DamagePopup {

    /** ポップアップの種別（色分け用）。 */
    public enum Kind {
        /** 通常ヒット（のけぞりを伴うフルダメージ）。 */
        HIT,
        /** ガード成立時の削り（chip）ダメージ。 */
        CHIP
    }

    private final int amount;
    private final Kind kind;
    private final float originX;
    private final float originY;
    private final int lifespan;
    private int age;

    /**
     * @param amount   表示するダメージ量（実際に減った HP）。
     * @param kind     種別（通常ヒット / ガード chip）。
     * @param originX  生成位置の中心 X（ワールド座標）。
     * @param originY  生成位置の中心 Y（ワールド座標）。ここから上昇する。
     * @param lifespan 表示フレーム数（{@code GameConstants.DAMAGE_POPUP_FRAMES}）。
     */
    public DamagePopup(int amount, Kind kind, float originX, float originY, int lifespan) {
        this.amount = amount;
        this.kind = kind;
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

    /** 表示するダメージ量。 */
    public int getAmount() {
        return amount;
    }

    /** 種別（HIT / CHIP）。 */
    public Kind getKind() {
        return kind;
    }

    /** 生成位置の中心 X（ワールド座標）。 */
    public float getOriginX() {
        return originX;
    }

    /** 生成位置の中心 Y（ワールド座標）。 */
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
