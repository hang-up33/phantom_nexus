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

    /**
     * 下段（しゃがみ）攻撃の hitbox を置く Y オフセット（足元基準, px）。Task 31。
     *
     * <p>しゃがみ攻撃は技定義の {@code hitboxOffsetY}（立ち姿勢用で 90px 以上）を使わず、脚部のこの低位に
     * hitbox を出す。これによりしゃがみ食らい判定（{@code height/3} ≒ 80px, 足元〜80px）にも届く下段技になり、
     * 立っている相手の脚にも当たる。0 = 足元（地面ライン）。
     */
    public static final float LOW_ATTACK_HITBOX_OFFSET_Y = 0f;

    /**
     * 投げ（Task 35）の hitstun フレーム数。通常被弾（{@link #HITSTUN_FRAMES}）より長く、掴みの拘束を表す。
     * ガード不能の近接掴みが成立したときに被弾側へ適用する。
     */
    public static final int THROW_HITSTUN_FRAMES = 30;

    /**
     * 投げの knockback 倍率（{@link #KNOCKBACK_SPEED} に乗算）。通常被弾より強く吹き飛ばし、掴みからの放り投げを表す（Task 35）。
     */
    public static final float THROW_KNOCKBACK_SCALE = 1.6f;

    /**
     * 投げ抜け（throw tech, Task 36）の猶予窓（フレーム数）。投げボタンを押すとこのフレーム数だけ「抜け可能」状態になり、
     * その間に相手の投げを掴まれると投げ抜け（相互に弾かれ・ノーダメージ）になる。掴みの発生（startup+α）を跨げる長さにする。
     */
    public static final int THROW_TECH_WINDOW = 10;

    /**
     * 投げ抜け成立後の硬直フレーム数（Task 36）。両者がこの間だけ行動不能になり、{@link #THROW_TECH_PUSHBACK} で弾かれる。
     * のけぞり（{@link #HITSTUN_FRAMES}）より短く、ノーダメージのため読み合いがすぐ再開する。
     */
    public static final int THROW_TECH_FRAMES = 14;

    /** 投げ抜け時に両者へ与える相互 knockback の初速（px/frame）。互いに反対方向へ弾く（Task 36）。 */
    public static final float THROW_TECH_PUSHBACK = KNOCKBACK_SPEED;

    /**
     * ダメージ数値ポップアップの表示フレーム数（被弾 / ガード時に与ダメージ量を命中位置から浮かび上がらせる演出）。
     *
     * <p>命中位置からこのフレーム数だけ上昇しながら表示し、終盤でフェードアウトして消える。60fps 基準で
     * 約 0.67 秒。純粋な視覚演出のため戦闘結果には影響しない（HP 計算とは独立）。
     */
    public static final int DAMAGE_POPUP_FRAMES = 40;

    /**
     * ヒットスパーク（命中時の火花エフェクト）の表示フレーム数（Task 38）。命中位置で放射状の火花が
     * このフレーム数だけ拡大＋フェードして消える。60fps 基準で約 0.2 秒の短い手応え演出。純粋な視覚演出で
     * 戦闘結果には影響しない。
     */
    public static final int HIT_SPARK_FRAMES = 12;

    /**
     * ラウンド開始イントロ（"ROUND N" → "FIGHT!" 演出）の総フレーム数（Task 42）。各ラウンド開始時、
     * この間はファイター操作・判定・タイマーを停止して開始演出を表示し、0 になった瞬間から戦闘開始。
     * 60fps 基準で約 1.5 秒。撮影モードでは既定でスキップ（{@code -x intro=true} で有効化）し、
     * 既存スクショレシピ（frame1 から戦闘前提）の後方互換を保つ。
     */
    public static final int ROUND_INTRO_FRAMES = 90;

    /**
     * ガードゲージの最大値（Task 43）。ガード成立（chip 被弾）のたびに攻撃力に応じて減り、0 になると
     * ガードクラッシュ（{@link #GUARD_BREAK_FRAMES} の行動不能＋ガード不能）。非ガード時は毎フレーム
     * {@link #GUARD_REGEN_PER_FRAME} 回復する。連続ガードは安全ではない＝崩しの読み合いを成立させる。
     */
    public static final float GUARD_GAUGE_MAX = 100f;

    /**
     * ガード 1 回あたりのゲージ減少量の除数（Task 43）。減少量 = {@code max(1, 攻撃力 / GUARD_DRAIN_DIVISOR)}。
     * 強い技ほど大きく削る（例：80 ダメージの中攻撃で 20 減＝5 回ガードで崩れる）。
     */
    public static final int GUARD_DRAIN_DIVISOR = 4;

    /** 非ガード時のガードゲージ回復量（フレームあたり・Task 43）。約 250f（≒4 秒）で満タンに戻る。 */
    public static final float GUARD_REGEN_PER_FRAME = 0.4f;

    /**
     * ガードクラッシュ時の行動不能フレーム数（Task 43）。ゲージが尽きると防御側はこのフレーム数だけ
     * ガード不能・行動不能になり（hitstun を流用）、攻撃側のフル確定反撃を許す。60fps 基準で約 0.67 秒。
     */
    public static final int GUARD_BREAK_FRAMES = 40;

    /**
     * ラウンド開始イントロのうち末尾の "FIGHT!" を表示するフレーム数（Task 42）。{@link #ROUND_INTRO_FRAMES}
     * の残りがこの値以下になったら "ROUND N" から "FIGHT!" 表示へ切り替える。60fps 基準で約 0.5 秒。
     */
    public static final int FIGHT_FLASH_FRAMES = 30;
}
