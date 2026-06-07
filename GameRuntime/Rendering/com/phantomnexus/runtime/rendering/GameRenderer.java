package com.phantomnexus.runtime.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.phantomnexus.runtime.battle.AttackPhase;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Move;

/**
 * バトルシーンの描画担当（Task 6: キャラクター描画 / Task 7: 移動・向き）。
 *
 * <p>背景クリア → 床 + キャラクター矩形 + 向きマーカー + アニメフレームピップ（{@link ShapeRenderer}）→
 * タイトル / 名前 / アニメ状態ラベル / 入力 HUD（{@link SpriteBatch}）の順に描く。キャラクターはスプライト
 * 導入前のプレースホルダとして、{@link Fighter} の現在位置・{@link Character} の寸法どおりの塗り矩形で表示し、
 * {@link FighterAnimator} の縦ボブで待機 / 歩行のアニメ進行を可視化する。向きは前面側に置く小矩形のマーカーで
 * 示す。Task 9 は状態機械 + フレームタイミングの管理を実装し、実スプライトは Task 15/16（JSON）で差し替える。
 */
public class GameRenderer {

    private static final Color GROUND_COLOR = new Color(0.16f, 0.17f, 0.22f, 1f);
    private static final Color P1_COLOR = new Color(0.30f, 0.55f, 0.92f, 1f);
    private static final Color P2_COLOR = new Color(0.92f, 0.42f, 0.36f, 1f);
    private static final Color FACING_COLOR = new Color(0.96f, 0.96f, 0.98f, 1f);
    private static final Color PIP_ON_COLOR = new Color(0.98f, 0.86f, 0.30f, 1f);
    private static final Color PIP_OFF_COLOR = new Color(0.35f, 0.36f, 0.42f, 1f);
    private static final Color HP_BACK_COLOR = new Color(0.12f, 0.12f, 0.16f, 1f);
    private static final Color HP_FRAME_COLOR = new Color(0.85f, 0.86f, 0.92f, 1f);
    private static final Color HP_FILL_HIGH = new Color(0.30f, 0.82f, 0.40f, 1f);
    private static final Color HP_FILL_MID = new Color(0.95f, 0.80f, 0.25f, 1f);
    private static final Color HP_FILL_LOW = new Color(0.90f, 0.28f, 0.24f, 1f);
    private static final Color ATK_STARTUP_COLOR = new Color(0.96f, 0.82f, 0.28f, 0.85f);
    private static final Color ATK_ACTIVE_COLOR = new Color(0.95f, 0.25f, 0.22f, 0.9f);
    private static final Color ATK_RECOVERY_COLOR = new Color(0.55f, 0.57f, 0.64f, 0.8f);
    private static final Color CONTACT_COLOR = new Color(1f, 1f, 1f, 1f);
    private static final float MARKER_SIZE = 18f;
    private static final float PIP_SIZE = 8f;
    private static final float PIP_GAP = 5f;
    // HP ゲージのレイアウト（HUD 上端）。左右に 1 本ずつ、中央寄せでミラー配置する。
    private static final float HP_BAR_WIDTH = 480f;
    private static final float HP_BAR_HEIGHT = 26f;
    private static final float HP_BAR_MARGIN = 40f;
    private static final float HP_BAR_TOP = 60f;
    private static final float HP_FRAME_THICKNESS = 3f;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    public GameRenderer() {
        camera = new OrthographicCamera();
        // 仮想解像度を固定し、ウィンドウサイズに応じてレターボックスでフィットさせる。
        viewport = new FitViewport(GameConstants.WORLD_WIDTH, GameConstants.WORLD_HEIGHT, camera);
        viewport.apply(true);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        // MVP では LibGDX 組込みフォント（Arial 15px）を使用。後続でビットマップフォントに差し替え可。
        font = new BitmapFont();
    }

    /**
     * バトルシーンを 1 フレーム描画する。
     *
     * @param p1           プレイヤー 1 のファイター（青）
     * @param p2           プレイヤー 2 のファイター（赤）
     * @param anim1        プレイヤー 1 のアニメーション状態
     * @param anim2        プレイヤー 2 のアニメーション状態
     * @param controlsHint 操作ガイド（HUD）
     * @param statusLine   各ファイターの座標 / 向き（HUD・移動の動作確認用）
     */
    public void renderScene(Fighter p1, Fighter p2, FighterAnimator anim1, FighterAnimator anim2,
                            String controlsHint, String statusLine) {
        ScreenUtils.clear(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, GameConstants.BG_A);
        camera.update();

        // --- 床 + キャラクター矩形 + 向きマーカー + アニメフレームピップ ---
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(GROUND_COLOR);
        shapes.rect(0f, 0f, GameConstants.WORLD_WIDTH, GameConstants.GROUND_Y);
        drawFighter(p1, anim1, P1_COLOR);
        drawFighter(p2, anim2, P2_COLOR);
        // ヒット接触マーカー（active hitbox × 相手 hurtbox が重なるフレームに点灯）。
        drawContactMarker(p1, p2);
        drawContactMarker(p2, p1);
        // HP ゲージ（HUD 上端）。P1 は左から、P2 は右から減る方向に塗る。
        drawHpBar(p1, true);
        drawHpBar(p2, false);
        shapes.end();

        // --- テキスト（タイトル / 名前 + アニメ状態ラベル / HP 数値 / 入力 HUD） ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.5f);
        drawCentered(GameConstants.WINDOW_TITLE, GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 30f);
        font.getData().setScale(1.0f);
        drawHpLabels(p1, true);
        drawHpLabels(p2, false);
        drawNameLabel(p1, anim1);
        drawNameLabel(p2, anim2);
        drawCentered(controlsHint, GameConstants.WORLD_WIDTH / 2f, 70f);
        drawCentered(statusLine, GameConstants.WORLD_WIDTH / 2f, 40f);
        batch.end();
    }

    /**
     * HP ゲージを 1 本描く。{@code leftAnchored} の側（P1=左 / P2=右）に枠を固定し、減少分は
     * 中央側から減る方向に塗る（対戦ゲームの定番配置）。色は残量に応じて緑→黄→赤へ変える。
     */
    private void drawHpBar(Fighter f, boolean leftAnchored) {
        float top = GameConstants.WORLD_HEIGHT - HP_BAR_TOP;
        float barBottom = top - HP_BAR_HEIGHT;
        float outerLeft = leftAnchored
                ? HP_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        // 枠（縁取り）→ 背景 → 残量フィルの順で重ねる。
        shapes.setColor(HP_FRAME_COLOR);
        shapes.rect(outerLeft - HP_FRAME_THICKNESS, barBottom - HP_FRAME_THICKNESS,
                HP_BAR_WIDTH + HP_FRAME_THICKNESS * 2f, HP_BAR_HEIGHT + HP_FRAME_THICKNESS * 2f);
        shapes.setColor(HP_BACK_COLOR);
        shapes.rect(outerLeft, barBottom, HP_BAR_WIDTH, HP_BAR_HEIGHT);
        float ratio = f.getHpRatio();
        float fillWidth = HP_BAR_WIDTH * ratio;
        // 減少は中央側から：左アンカーは左端固定で右が縮み、右アンカーは右端固定で左が縮む。
        float fillLeft = leftAnchored ? outerLeft : outerLeft + (HP_BAR_WIDTH - fillWidth);
        shapes.setColor(hpFillColor(ratio));
        shapes.rect(fillLeft, barBottom, fillWidth, HP_BAR_HEIGHT);
    }

    /** 残量割合に応じた HP フィル色（高=緑 / 中=黄 / 低=赤）。 */
    private static Color hpFillColor(float ratio) {
        if (ratio > 0.5f) {
            return HP_FILL_HIGH;
        }
        return ratio > 0.25f ? HP_FILL_MID : HP_FILL_LOW;
    }

    /** HP ゲージに重ねる名前（外側寄せ）と HP 数値（内側寄せ）のラベル。 */
    private void drawHpLabels(Fighter f, boolean leftAnchored) {
        float top = GameConstants.WORLD_HEIGHT - HP_BAR_TOP;
        float labelY = top + 18f;
        float outerLeft = leftAnchored
                ? HP_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        String hp = f.getCurrentHp() + " / " + f.getMaxHp();
        if (leftAnchored) {
            font.draw(batch, f.getDef().getName(), outerLeft, labelY);
            layout.setText(font, hp);
            font.draw(batch, layout, outerLeft + HP_BAR_WIDTH - layout.width, labelY);
        } else {
            layout.setText(font, f.getDef().getName());
            font.draw(batch, layout, outerLeft + HP_BAR_WIDTH - layout.width, labelY);
            font.draw(batch, hp, outerLeft, labelY);
        }
    }

    /**
     * ファイターをプレースホルダ矩形で描く。アニメーションの縦ボブを位置に反映し、向きマーカーと
     * 現在フレームを示すピップ列を添える（スプライト導入までの可視化）。
     */
    private void drawFighter(Fighter f, FighterAnimator anim, Color color) {
        Character d = f.getDef();
        float left = f.getX() - d.getWidth() / 2f;
        // 待機 / 歩行の進行を縦ボブで可視化（空中は物理で位置が変わるためボブ 0）。
        float bottom = f.getY() + anim.bobOffset();
        // のけぞり中は白くフラッシュして被弾を可視化する。
        shapes.setColor(f.isInHitstun() ? hitstunFlash(color) : color);
        shapes.rect(left, bottom, d.getWidth(), d.getHeight());
        // 向きマーカー：上部の前面側に小矩形を置く。
        float markerY = bottom + d.getHeight() - MARKER_SIZE - 12f;
        float markerX = f.isFacingRight()
                ? left + d.getWidth() - MARKER_SIZE - 8f
                : left + 8f;
        shapes.setColor(FACING_COLOR);
        shapes.rect(markerX, markerY, MARKER_SIZE, MARKER_SIZE);
        // 攻撃中は前方の strike 矩形を区間色（startup=黄 / active=赤 / recovery=灰）で描く。
        if (f.isAttacking()) {
            drawAttackStrike(f);
        }
        // フレームピップ：足元下に総フレーム数だけ並べ、現在フレームを点灯（アニメ進行の証跡）。
        drawFramePips(f, anim);
    }

    /**
     * 攻撃の strike 矩形（技の hitbox 位置）を区間色で描く（Task 11 の可視化）。
     *
     * <p>hitbox は技定義の「前方の前面・足元」基準の相対座標で、向きに応じて左右反転する。
     * 実際の当たり判定（hurtbox との重なり）は Task 12、デバッグ枠表示は Task 18 で扱う。
     */
    private void drawAttackStrike(Fighter f) {
        Move m = f.getDef().getNormalAttack();
        if (m == null) {
            return;
        }
        Character d = f.getDef();
        float frontX = f.isFacingRight()
                ? f.getX() + d.getWidth() / 2f
                : f.getX() - d.getWidth() / 2f;
        float boxX = f.isFacingRight()
                ? frontX + m.getHitboxOffsetX()
                : frontX - m.getHitboxOffsetX() - m.getHitboxWidth();
        float boxY = f.getY() + m.getHitboxOffsetY();
        shapes.setColor(attackPhaseColor(f.getAttackPhase()));
        shapes.rect(boxX, boxY, m.getHitboxWidth(), m.getHitboxHeight());
    }

    /** のけぞり用のフラッシュ色（元色を白へ寄せる）。 */
    private static Color hitstunFlash(Color base) {
        return new Color(
                base.r + (1f - base.r) * 0.6f,
                base.g + (1f - base.g) * 0.6f,
                base.b + (1f - base.b) * 0.6f,
                1f);
    }

    /** active hitbox が相手 hurtbox に重なるフレームに、接触位置へ白い火花マーカーを描く（Task 12）。 */
    private void drawContactMarker(Fighter attacker, Fighter defender) {
        if (!CollisionSystem.isHitting(attacker, defender)) {
            return;
        }
        Hitbox hb = CollisionSystem.activeHitbox(attacker);
        float cx = hb.getX() + hb.getWidth() / 2f;
        float cy = hb.getY() + hb.getHeight() / 2f;
        float s = 28f;
        shapes.setColor(CONTACT_COLOR);
        shapes.rect(cx - s / 2f, cy - s / 2f, s, s);
    }

    /** 攻撃区間に応じた strike 色（startup=黄 / active=赤 / recovery=灰）。 */
    private static Color attackPhaseColor(AttackPhase phase) {
        switch (phase) {
            case STARTUP:
                return ATK_STARTUP_COLOR;
            case ACTIVE:
                return ATK_ACTIVE_COLOR;
            case RECOVERY:
                return ATK_RECOVERY_COLOR;
            default:
                return ATK_RECOVERY_COLOR;
        }
    }

    /** 現在のアニメフレームを示すピップ列を矩形の足元下に描く。 */
    private void drawFramePips(Fighter f, FighterAnimator anim) {
        int count = anim.getState().frameCount();
        int active = anim.getFrameIndex();
        float totalWidth = count * PIP_SIZE + (count - 1) * PIP_GAP;
        float startX = f.getX() - totalWidth / 2f;
        float y = f.getY() - PIP_SIZE - 6f;
        for (int i = 0; i < count; i++) {
            shapes.setColor(i == active ? PIP_ON_COLOR : PIP_OFF_COLOR);
            shapes.rect(startX + i * (PIP_SIZE + PIP_GAP), y, PIP_SIZE, PIP_SIZE);
        }
    }

    /** ファイターの名前と現在の状態（攻撃中は区間、それ以外はアニメ状態 / フレーム）を矩形の上に表示する。 */
    private void drawNameLabel(Fighter f, FighterAnimator anim) {
        float centerX = f.getX();
        float top = f.getY() + f.getDef().getHeight();
        drawCentered(f.getDef().getName(), centerX, top + 30f);
        String stateLabel;
        if (f.isInHitstun()) {
            stateLabel = "hitstun " + f.getHitstunFrames();
        } else if (f.isAttacking()) {
            stateLabel = "attack:" + f.getAttackPhase().name().toLowerCase();
        } else {
            stateLabel = anim.getState().label() + " f" + anim.getFrameIndex();
        }
        drawCentered(stateLabel, centerX, top + 12f);
    }

    /** 指定文字列を中心 X（{@code centerX}）・ベースライン Y（{@code y}）に水平センタリングで描く。 */
    private void drawCentered(String text, float centerX, float y) {
        layout.setText(font, text);
        font.draw(batch, layout, centerX - layout.width / 2f, y);
    }

    /** ウィンドウリサイズ時にビューポートを追従させる。 */
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /** GPU リソースの解放。 */
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
