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

    /** 1 フレーム分の描画。背景クリア → タイトル文字を画面中央に描画。 */
    public void render() {
        ScreenUtils.clear(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, GameConstants.BG_A);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        final String title = GameConstants.WINDOW_TITLE;
        layout.setText(font, title);
        font.draw(batch, layout,
                (GameConstants.WORLD_WIDTH - layout.width) / 2f,
                (GameConstants.WORLD_HEIGHT + layout.height) / 2f);
        batch.end();
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
