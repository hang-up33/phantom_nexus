package com.phantomnexus.runtime.core;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.phantomnexus.shared.constants.GameConstants;

/**
 * デスクトップ（LWJGL3）エントリポイント。
 *
 * <p>build.gradle の {@code application.mainClass} と一致させること
 * （{@code com.phantomnexus.runtime.core.DesktopLauncher}）。
 * ウィンドウ表示の設定（タイトル・初期サイズ・vsync・FPS 上限・リサイズ可否）は
 * すべて {@link GameConstants} 経由で与え、ハードコードしない（Task 4: ウィンドウ表示）。
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
        // エントリポイント専用（インスタンス化禁止）
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(GameConstants.WINDOW_TITLE);
        config.setWindowedMode(GameConstants.WORLD_WIDTH, GameConstants.WORLD_HEIGHT);
        // 固定窓（MVP は固定解像度）。仮想解像度はビューポートで維持する。
        config.setResizable(GameConstants.WINDOW_RESIZABLE);
        // 垂直同期 + 前景 FPS 上限で 60fps 基準に揃える（BattleSystem.md の固定ステップ前提）。
        config.useVsync(GameConstants.VSYNC);
        config.setForegroundFPS(GameConstants.TARGET_FPS);
        new Lwjgl3Application(new PhantomNexusGame(), config);
    }
}
