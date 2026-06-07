package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.phantomnexus.runtime.rendering.GameRenderer;

/**
 * Phantom Nexus アプリケーション本体（ゲームループ / ライフサイクル）。
 *
 * <p>Core はライフサイクル（create/render/resize/dispose）の制御に専念し、
 * 実際の描画は {@link GameRenderer}（Rendering）へ委譲する（Task 3）。
 */
public class PhantomNexusGame extends ApplicationAdapter {

    private GameRenderer renderer;

    @Override
    public void create() {
        renderer = new GameRenderer();
    }

    @Override
    public void render() {
        renderer.render();
    }

    @Override
    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    @Override
    public void dispose() {
        renderer.dispose();
    }
}
