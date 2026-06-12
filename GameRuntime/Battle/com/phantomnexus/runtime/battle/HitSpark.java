package com.phantomnexus.runtime.battle;

/**
 * ヒットスパーク 1 件分の実行時状態（命中位置に出す火花の手応え演出）（Task 38）。
 *
 * <p>ヒット解決（{@code PhantomNexusGame.resolveHit}）や飛び道具命中時に命中位置へ生成し、毎フレーム
 * {@link #update()} で経過フレームを数える。描画側（{@code GameRenderer}）が経過フレームから拡大率と
 * フェードを導出して放射状の火花を描く。{@link Projectile} / {@link DamagePopup} と同じく状態（原点・種別・
 * 寿命）だけを持つ POJO とし、描画リソースや描画ロジックは持たない。
 *
 * <p>純粋な視覚演出であり HP 計算とは独立（戦闘結果に影響しない）。種別（{@link Kind}）で通常ヒットと
 * ガード成立を色分けする。寿命を過ぎたら {@code PhantomNexusGame} 側が一覧から取り除く。
 */
public class HitSpark {

    /** スパークの種別（色分け用）。 */
    public enum Kind {
        /** 通常ヒット（のけぞり / 投げを伴うクリーンヒット）。 */
        HIT,
        /** ガード成立（防御された接触）。 */
        GUARD
    }

    private final Kind kind;
    private final float originX;
    private final float originY;
    private final int lifespan;
    private int age;

    /**
     * @param kind     種別（通常ヒット / ガード）。
     * @param originX  命中位置の中心 X（ワールド座標）。
     * @param originY  命中位置の中心 Y（ワールド座標）。
     * @param lifespan 表示フレーム数（{@code GameConstants.HIT_SPARK_FRAMES}）。
     */
    public HitSpark(Kind kind, float originX, float originY, int lifespan) {
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

    /** 種別（HIT / GUARD）。 */
    public Kind getKind() {
        return kind;
    }

    /** 命中位置の中心 X（ワールド座標）。 */
    public float getOriginX() {
        return originX;
    }

    /** 命中位置の中心 Y（ワールド座標）。 */
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
