package com.phantomnexus.runtime.core;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.phantomnexus.shared.constants.GameConstants;

/**
 * デスクトップ（LWJGL3）エントリポイント。
 *
 * <p>build.gradle の {@code application.mainClass} と一致させること
 * （{@code com.phantomnexus.runtime.core.DesktopLauncher}）。
 * Task 3 では初期画面を表示できる最小構成で起動する。ウィンドウ表示の詳細設定
 * （vsync / FPS 上限 / リサイズ可否 等）は Task 4 で拡張する。
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
        // エントリポイント専用（インスタンス化禁止）
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(GameConstants.WINDOW_TITLE);
        config.setWindowedMode(GameConstants.WORLD_WIDTH, GameConstants.WORLD_HEIGHT);
        new Lwjgl3Application(new PhantomNexusGame(), config);
    }
}
