package com.phantomnexus.runtime.rendering;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.phantomnexus.shared.constants.GameConstants;

/**
 * 初期画面の描画担当（Task 3: LibGDX 初期画面作成）。
 *
 * <p>{@link SpriteBatch}・カメラ・ビューポート・フォントを保持し、背景クリアと
 * タイトル文字の描画を行う。後続タスクでスプライト/アニメーション描画を追加していく。
 * 描画の実体は本クラス（Rendering）に閉じ、ゲームループ（Core）からは委譲で呼ぶ。
 */
public class GameRenderer {

    private final SpriteBatch batch;
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
        // MVP では LibGDX 組込みフォント（Arial 15px）を使用。後続でビットマップフォントに差し替え可。
        font = new BitmapFont();
        font.getData().setScale(2.0f);
    }

    /**
     * 1 フレーム分の描画。背景クリア → タイトル + 操作ガイド + 現在の入力状態を描画する。
     *
     * @param controlsHint キー割当の操作ガイド（{@code PlayerInput.describe()} 由来）
     * @param activeLine   そのフレームで押下中の論理アクション一覧（入力配線の動作確認用）
     */
    public void render(String controlsHint, String activeLine) {
        ScreenUtils.clear(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, GameConstants.BG_A);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        final float centerX = GameConstants.WORLD_WIDTH / 2f;
        // タイトル（大）
        font.getData().setScale(2.0f);
        drawCentered(GameConstants.WINDOW_TITLE, centerX, GameConstants.WORLD_HEIGHT / 2f + 80f);
        // 操作ガイド / 入力状態（小）
        font.getData().setScale(1.0f);
        drawCentered(controlsHint, centerX, GameConstants.WORLD_HEIGHT / 2f - 20f);
        drawCentered(activeLine, centerX, GameConstants.WORLD_HEIGHT / 2f - 60f);

        batch.end();
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
        font.dispose();
    }
}
