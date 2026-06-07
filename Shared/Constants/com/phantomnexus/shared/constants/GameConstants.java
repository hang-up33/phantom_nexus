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

    /**
     * 目標フレームレート。BattleSystem.md の「60fps 固定ステップ」基準であり、
     * 同時にウィンドウの前景 FPS 上限としても使用する。
     */
    public static final int TARGET_FPS = 60;

    /** 垂直同期。ティアリング防止のため既定で有効。 */
    public static final boolean VSYNC = true;

    /**
     * ウィンドウのリサイズ可否。MVP は固定解像度の格闘ゲームのため固定窓（false）。
     * 仮想解像度（{@link #WORLD_WIDTH}×{@link #WORLD_HEIGHT}）はビューポートで維持する。
     */
    public static final boolean WINDOW_RESIZABLE = false;

    /** 地面（床）のワールド Y 座標。キャラクターの足元（描画原点の下端）が乗る基準線。 */
    public static final float GROUND_Y = 120f;

    /** プレイヤー 1 の初期 X 座標（キャラクターの中心 X）。画面中央から左に配置。 */
    public static final float P1_SPAWN_X = 420f;

    /** プレイヤー 2 の初期 X 座標（キャラクターの中心 X）。画面中央から右に配置。 */
    public static final float P2_SPAWN_X = 860f;

    /** 重力加速度（px/frame^2）。ジャンプの落下に毎フレーム適用する（60fps 基準）。 */
    public static final float GRAVITY = 0.6f;

    /** のけぞり（hitstun）フレーム数。被弾側がこの間だけ行動不能になる（Task 13）。 */
    public static final int HITSTUN_FRAMES = 18;

    /** 被弾時の初速 knockback（px/frame, 後方へ）。毎フレーム {@link #KNOCKBACK_FRICTION} で減衰（Task 13）。 */
    public static final float KNOCKBACK_SPEED = 7f;

    /** knockback 速度の毎フレーム減衰率（0〜1）。小さいほど早く止まる（Task 13）。 */
    public static final float KNOCKBACK_FRICTION = 0.6f;
}
