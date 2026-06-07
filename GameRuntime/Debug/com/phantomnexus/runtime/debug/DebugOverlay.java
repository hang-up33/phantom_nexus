package com.phantomnexus.runtime.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Hurtbox;
import com.phantomnexus.shared.types.PushBox;

/**
 * 当たり判定のデバッグ表示（Task 18）。トグルで hit / hurt / push の AABB 枠を線で重ね描きする。
 *
 * <p>各矩形は {@link CollisionSystem} がファイターの実行時状態から毎フレーム生成するもので、
 * Battle のロジックが実際に使う判定と同一（脳内実行ではなく「実際にこう判定している」を可視化）。
 * 色分け：<b>pushbox=青 / hurtbox=緑 / hitbox(active)=赤</b>。トグルは {@link #toggle()}（既定 OFF）。
 *
 * <p>描画は呼び出し側（{@code GameRenderer}）の {@link ShapeRenderer} を借り、{@link ShapeRenderer.ShapeType#Line}
 * で自身の begin/end を行う（投影行列は呼び出し側で設定済みの前提）。
 */
public final class DebugOverlay {

    private static final Color PUSH_COLOR = new Color(0.35f, 0.6f, 1f, 1f);
    private static final Color HURT_COLOR = new Color(0.35f, 0.95f, 0.45f, 1f);
    private static final Color HIT_COLOR = new Color(1f, 0.3f, 0.3f, 1f);

    private boolean enabled;

    /** 表示の ON/OFF を反転する（F1 等のトグルキーから呼ぶ）。 */
    public void toggle() {
        enabled = !enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 有効時に両ファイターの判定枠を線で描く。無効時は何もしない。
     *
     * @param shapes 投影行列設定済みの {@link ShapeRenderer}（本メソッドが Line で begin/end する）
     */
    public void drawBoxes(ShapeRenderer shapes, Fighter p1, Fighter p2) {
        if (!enabled) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawFighterBoxes(shapes, p1);
        drawFighterBoxes(shapes, p2);
        shapes.end();
    }

    private static void drawFighterBoxes(ShapeRenderer shapes, Fighter f) {
        PushBox pb = CollisionSystem.pushbox(f);
        shapes.setColor(PUSH_COLOR);
        shapes.rect(pb.getX(), pb.getY(), pb.getWidth(), pb.getHeight());

        Hurtbox hu = CollisionSystem.hurtbox(f);
        shapes.setColor(HURT_COLOR);
        shapes.rect(hu.getX(), hu.getY(), hu.getWidth(), hu.getHeight());

        Hitbox hb = CollisionSystem.activeHitbox(f);
        if (hb != null) {
            shapes.setColor(HIT_COLOR);
            shapes.rect(hb.getX(), hb.getY(), hb.getWidth(), hb.getHeight());
        }
    }
}
