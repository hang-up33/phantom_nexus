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
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;

/**
 * バトルシーンの描画担当（Task 6: キャラクター描画）。
 *
 * <p>背景クリア → 床 + キャラクター矩形（{@link ShapeRenderer}）→ タイトル / 名前 / 入力 HUD
 * （{@link SpriteBatch}）の順に描く。キャラクターはスプライト導入前のプレースホルダとして、
 * {@link Character} の寸法どおりの塗り矩形で表示する。{@code x} はキャラ中心 X、足元は
 * {@link GameConstants#GROUND_Y}。スプライト / アニメーションは Task 9 以降で差し替える。
 */
public class GameRenderer {

    private static final Color GROUND_COLOR = new Color(0.16f, 0.17f, 0.22f, 1f);
    private static final Color P1_COLOR = new Color(0.30f, 0.55f, 0.92f, 1f);
    private static final Color P2_COLOR = new Color(0.92f, 0.42f, 0.36f, 1f);

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
     * @param p1           プレイヤー 1 のキャラクター定義
     * @param p1x          プレイヤー 1 の中心 X
     * @param p2           プレイヤー 2 のキャラクター定義
     * @param p2x          プレイヤー 2 の中心 X
     * @param controlsHint 操作ガイド（HUD）
     * @param activeLine   押下中アクション（HUD・入力配線の動作確認用）
     */
    public void renderScene(Character p1, float p1x, Character p2, float p2x,
                            String controlsHint, String activeLine) {
        ScreenUtils.clear(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, GameConstants.BG_A);
        camera.update();

        // --- 床 + キャラクター矩形（プレースホルダ） ---
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(GROUND_COLOR);
        shapes.rect(0f, 0f, GameConstants.WORLD_WIDTH, GameConstants.GROUND_Y);
        drawFighterBox(p1, p1x, P1_COLOR);
        drawFighterBox(p2, p2x, P2_COLOR);
        shapes.end();

        // --- テキスト（タイトル / 名前ラベル / 入力 HUD） ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.5f);
        drawCentered(GameConstants.WINDOW_TITLE, GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 30f);
        font.getData().setScale(1.0f);
        drawCentered(p1.getName(), p1x, GameConstants.GROUND_Y + p1.getHeight() + 30f);
        drawCentered(p2.getName(), p2x, GameConstants.GROUND_Y + p2.getHeight() + 30f);
        drawCentered(controlsHint, GameConstants.WORLD_WIDTH / 2f, 70f);
        drawCentered(activeLine, GameConstants.WORLD_WIDTH / 2f, 40f);
        batch.end();
    }

    /** キャラクターを中心 X・足元 {@link GameConstants#GROUND_Y} のプレースホルダ矩形で描く。 */
    private void drawFighterBox(Character c, float centerX, Color color) {
        shapes.setColor(color);
        shapes.rect(centerX - c.getWidth() / 2f, GameConstants.GROUND_Y, c.getWidth(), c.getHeight());
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
