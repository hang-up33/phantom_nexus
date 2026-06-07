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
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;

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
    private static final float MARKER_SIZE = 18f;
    private static final float PIP_SIZE = 8f;
    private static final float PIP_GAP = 5f;

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
        shapes.end();

        // --- テキスト（タイトル / 名前 + アニメ状態ラベル / 入力 HUD） ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.5f);
        drawCentered(GameConstants.WINDOW_TITLE, GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 30f);
        font.getData().setScale(1.0f);
        drawNameLabel(p1, anim1);
        drawNameLabel(p2, anim2);
        drawCentered(controlsHint, GameConstants.WORLD_WIDTH / 2f, 70f);
        drawCentered(statusLine, GameConstants.WORLD_WIDTH / 2f, 40f);
        batch.end();
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
        shapes.setColor(color);
        shapes.rect(left, bottom, d.getWidth(), d.getHeight());
        // 向きマーカー：上部の前面側に小矩形を置く。
        float markerY = bottom + d.getHeight() - MARKER_SIZE - 12f;
        float markerX = f.isFacingRight()
                ? left + d.getWidth() - MARKER_SIZE - 8f
                : left + 8f;
        shapes.setColor(FACING_COLOR);
        shapes.rect(markerX, markerY, MARKER_SIZE, MARKER_SIZE);
        // フレームピップ：足元下に総フレーム数だけ並べ、現在フレームを点灯（アニメ進行の証跡）。
        drawFramePips(f, anim);
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

    /** ファイターの名前と現在のアニメ状態 / フレームを矩形の上に表示する。 */
    private void drawNameLabel(Fighter f, FighterAnimator anim) {
        float centerX = f.getX();
        float top = f.getY() + f.getDef().getHeight();
        drawCentered(f.getDef().getName(), centerX, top + 30f);
        drawCentered(anim.getState().label() + " f" + anim.getFrameIndex(), centerX, top + 12f);
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
