package com.phantomnexus.shared.constants;

/**
 * 画面・描画まわりの定数（単一の真実）。
 *
 * <p>CLAUDE.md「アーキテクチャ」のとおり、画面サイズ・フレームレート・レイヤ順などの
 * 定数は {@code Shared/Constants} に集約する。各モジュールはハードコードせず本クラス経由で参照する。
 */
public final class GameConstants {

    private GameConstants() {
        // インスタンス化禁止（定数ホルダー）
    }

    /** 仮想解像度（ワールド座標）の幅。単位はピクセル。 */
    public static final int WORLD_WIDTH = 1280;

    /** 仮想解像度（ワールド座標）の高さ。単位はピクセル。 */
    public static final int WORLD_HEIGHT = 720;

    /** ウィンドウタイトル（OS のタイトルバー / 初期画面の表示名）。 */
    public static final String WINDOW_TITLE = "Phantom Nexus";

    /** 背景クリア色（RGBA, 各 0.0〜1.0）。 */
    public static final float BG_R = 0.07f;
    public static final float BG_G = 0.08f;
    public static final float BG_B = 0.12f;
    public static final float BG_A = 1.0f;
}
