package com.phantomnexus.runtime.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.phantomnexus.runtime.battle.AttackPhase;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.DamagePopup;
import com.phantomnexus.runtime.battle.HitSpark;
import com.phantomnexus.runtime.battle.LandingDust;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.runtime.battle.Projectile;
import com.phantomnexus.runtime.battle.RoundManager;
import com.phantomnexus.runtime.debug.DebugOverlay;

import java.util.List;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Move;
import com.phantomnexus.shared.types.Stage;
import com.phantomnexus.shared.types.StageLayer;

/**
 * バトルシーンの描画担当（Task 6: キャラクター描画 / Task 7: 移動・向き）。
 *
 * <p>描画パスの順序：(1) ステージ背景 + 床（{@link ShapeRenderer}）→ (2) キャラクターのスプライト
 * （{@link SpriteBatch} + {@link SpriteLibrary}。Task 34）→ (3) ガード / 攻撃 strike / 接触 / 飛び道具 /
 * HP ゲージ等のオーバーレイ（{@link ShapeRenderer}）→ (4) デバッグ判定枠 → (5) タイトル / 名前 / 状態
 * ラベル / 入力 HUD（{@link SpriteBatch}）。
 *
 * <p>キャラクターは JSON にスプライト定義（{@link Character#getSprite()}）があれば {@link SpriteLibrary} が
 * 切り出した {@link TextureRegion} を、{@link FighterAnimator} の状態（→行）・フレーム（→列）に同期して
 * 向き反転つきで描く（Task 34）。スプライト未指定のキャラは従来どおりプレースホルダ矩形 + 向きマーカーで
 * 描画し、{@link FighterAnimator} の縦ボブでアニメ進行を可視化する（後方互換）。
 */
public class GameRenderer {

    // ステージ未設定時のフォールバック色（Task 17 でステージ JSON から差し替え）。
    private static final Color GROUND_COLOR = new Color(0.16f, 0.17f, 0.22f, 1f);
    private static final Color SKY_TOP_FALLBACK = new Color(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, 1f);
    private static final Color SKY_BOTTOM_FALLBACK = new Color(0.14f, 0.16f, 0.24f, 1f);
    private static final Color P1_COLOR = new Color(0.30f, 0.55f, 0.92f, 1f);
    private static final Color P2_COLOR = new Color(0.92f, 0.42f, 0.36f, 1f);
    // ミラーマッチ（同キャラ対戦）で P2 を識別するためのパレットスワップ乗算色（Task 62）。
    // スプライト / 矩形のキャラ色に乗算して色相をずらし、左右どちらが自分かを一目で分かるようにする。
    private static final Color MIRROR_P2_TINT = new Color(1f, 0.45f, 0.55f, 1f);
    private static final Color FACING_COLOR = new Color(0.96f, 0.96f, 0.98f, 1f);
    private static final Color PIP_ON_COLOR = new Color(0.98f, 0.86f, 0.30f, 1f);
    private static final Color PIP_OFF_COLOR = new Color(0.35f, 0.36f, 0.42f, 1f);
    private static final Color HP_BACK_COLOR = new Color(0.12f, 0.12f, 0.16f, 1f);
    private static final Color HP_FRAME_COLOR = new Color(0.85f, 0.86f, 0.92f, 1f);
    private static final Color HP_FILL_HIGH = new Color(0.30f, 0.82f, 0.40f, 1f);
    private static final Color HP_FILL_MID = new Color(0.95f, 0.80f, 0.25f, 1f);
    private static final Color HP_FILL_LOW = new Color(0.90f, 0.28f, 0.24f, 1f);
    private static final Color HP_RECOVERABLE_COLOR = new Color(0.66f, 0.15f, 0.16f, 1f); // 回復可能ダメージ（レッドライフ）の赤ゲージ（Task 104）
    private static final Color ATK_STARTUP_COLOR = new Color(0.96f, 0.82f, 0.28f, 0.85f);
    private static final Color ATK_ACTIVE_COLOR = new Color(0.95f, 0.25f, 0.22f, 0.9f);
    private static final Color ATK_RECOVERY_COLOR = new Color(0.55f, 0.57f, 0.64f, 0.8f);
    // 投げ（grab box）の strike 矩形色。通常攻撃（黄→赤→灰）と区別する紫（Task 35）。
    private static final Color THROW_COLOR = new Color(0.82f, 0.38f, 0.95f, 0.9f);
    private static final Color CONTACT_COLOR = new Color(1f, 1f, 1f, 1f);
    private static final Color PROJECTILE_CORE = new Color(1f, 0.95f, 0.7f, 1f);
    private static final Color PROJECTILE_GLOW = new Color(0.45f, 0.85f, 1f, 1f);
    // 飛び道具の軌跡（motion trail・Task 134）：過去位置に薄く小さい円の尾を引く（弾のグロー色を流用）。
    private static final float PROJECTILE_TRAIL_ALPHA = 0.5f;     // 最新の尾の不透明度（最古は 0 へ線形）
    private static final float PROJECTILE_TRAIL_MIN_SCALE = 0.30f; // 最古の尾の半径＝グロー半径 × これ
    private static final float PROJECTILE_TRAIL_MAX_SCALE = 0.85f; // 最新の尾の半径＝グロー半径 × これ
    private static final Color GUARD_COLOR = new Color(0.30f, 0.70f, 1f, 0.55f);
    // クリーンヒットの白フラッシュ（impact flash・Task 136）。被弾直後の数フレーム、被弾側を発光させて手応えを強める。
    // スプライトキャラは加算合成でシルエットを発光（IMPACT_FLASH_PEAK＝最大加算強度）、矩形フォールバックは白を上重ね。
    private static final Color IMPACT_FLASH_COLOR = new Color(1f, 1f, 1f, 0.7f); // 矩形版：a は残りフレーム比で減衰
    private static final float IMPACT_FLASH_PEAK = 0.85f;                         // スプライト版：加算の最大強度（残りフレーム比で減衰）

    // 足元の影（純描画演出。Task 130）。床に置く半透明の楕円で、滞空高さに応じて縮小・減光する。
    private static final Color SHADOW_COLOR = new Color(0f, 0f, 0f, 0.34f);
    private static final float SHADOW_WIDTH_SCALE = 1.05f; // 影の横径＝キャラ幅 × これ（接地時）
    private static final float SHADOW_HEIGHT = 13f;        // 影の縦径（楕円の薄さ・接地時）
    private static final float SHADOW_AIR_FALLOFF = 240f;  // 滞空高さに対する縮小・減光の基準（px）
    private static final float SHADOW_MIN_SCALE = 0.45f;   // 滞空時に縮む下限
    // 必殺技ゲージ満タンのオーラ（Task 137）。EX / スーパーが撃てる合図として足元に金色のパルス光輪を出す。
    private static final Color METER_AURA_COLOR = new Color(1f, 0.82f, 0.25f, 0.38f); // 満タンメーターと同系の金
    private static final float METER_AURA_WIDTH_SCALE = 1.45f; // 光輪の横径＝キャラ幅 × これ（基準）
    private static final float METER_AURA_HEIGHT = 20f;        // 光輪の縦径（楕円の薄さ・基準）
    private static final float METER_AURA_PULSE = 0.22f;       // パルスで増減する割合（±）
    private static final float METER_AURA_PERIOD = 36f;        // パルス周期（描画フレーム）
    // 背景の浮遊パーティクル（ambient motes・Task 139）。空気感を出す微かな光の粒（純描画・乱数なし＝決定的）。
    private static final Color MOTE_COLOR = new Color(0.78f, 0.86f, 1f, 0.12f); // 淡い水色・低不透明度
    private static final int MOTE_COUNT = 30;       // 粒の数
    private static final float MOTE_RADIUS = 3.5f;  // 粒の基準半径（px）
    private static final float MOTE_DRIFT = 26f;    // 横揺れの振幅（px）
    private static final float MOTE_RISE = 0.35f;   // 1 描画フレームあたりの上昇量（px）
    private static final float MOTE_DRIFT_SPEED = 0.018f; // 横揺れの角速度
    // 空の光の帯（sky sweep・Task 147）。背景をゆっくり横切る淡い光の縦帯で空気感を出す（純描画・決定的）。
    private static final Color SKY_SWEEP_COLOR = new Color(0.85f, 0.90f, 1f, 1f);
    private static final float SKY_SWEEP_WIDTH = 260f;   // 光の帯の幅（px）
    private static final float SKY_SWEEP_ALPHA = 0.16f;  // 帯中心の最大不透明度
    private static final float SKY_SWEEP_SPEED = 2.4f;   // 1 描画フレームあたりの横移動（px）
    // 低 HP 警告ビネット（low-HP vignette・Task 145）。どちらかの HP が低いと画面端を赤く脈動させ危機感を出す。
    private static final float LOW_HP_RATIO = 0.25f;     // この残量割合以下で警告
    private static final Color LOW_HP_VIGNETTE_COLOR = new Color(0.85f, 0.10f, 0.10f, 1f); // 端の赤
    private static final float LOW_HP_VIGNETTE_BAND = 130f; // 端から内側へ赤がフェードする帯の幅（px）
    private static final float LOW_HP_VIGNETTE_ALPHA = 0.5f; // 端の最大不透明度（脈動で増減）
    // HP バーのダメージトレイル（Task 146）。被弾で減った分を明るい色の遅れバーで一瞬残し、ダメージ量を伝える。
    private static final Color HP_TRAIL_COLOR = new Color(0.98f, 0.92f, 0.55f, 0.9f); // 遅延ドレインの明るい黄
    private static final float HP_TRAIL_DRAIN = 0.010f;  // 1 描画フレームあたりに遅延バーが詰める割合
    // KO 縁フラッシュ（Task 148）。KO 決着の瞬間に画面の縁を白く光らせて余韻を作る（数フレームでフェード）。
    // ソフトウェア GL では全画面ソリッド rect の半透明合成が不安定なため、動作実績のある縁グラデーション rect を使う。
    private static final int KO_FLASH_FRAMES = 14;       // フラッシュ持続フレーム
    private static final Color KO_FLASH_COLOR = new Color(1f, 1f, 1f, 0.85f); // 縁の白（α は残りフレーム比で減衰）
    private static final float KO_FLASH_BAND = 220f;     // 縁から内側へ白がフェードする帯の幅（px）
    // 勝者グロー（Task 149）。決着 / ラウンド間に勝者の足元へ金色のパルス光輪を出して際立たせる。
    private static final Color WINNER_GLOW_COLOR = new Color(1f, 0.85f, 0.30f, 0.55f);
    private static final float WINNER_GLOW_WIDTH_SCALE = 1.9f; // 勝者光輪の横径＝キャラ幅 × これ
    private static final float WINNER_GLOW_HEIGHT = 34f;       // 勝者光輪の縦径
    // 勝利の光の粒（victory sparkles・Task 150）。決着 / ラウンド間に勝者の周囲を金色の光の粒が舞い上がる祝祭演出。
    // （半透明の黒は本環境のソフトウェア GL で暗転にならないため、暗転でなく非黒の祝祭演出にした）。
    private static final Color VICTORY_SPARKLE_COLOR = new Color(1f, 0.88f, 0.42f, 0.85f); // 金色の粒
    private static final int VICTORY_SPARKLE_COUNT = 16;   // 粒の数
    private static final float VICTORY_SPARKLE_SPREAD = 120f; // 勝者中心からの横の散らばり（px）
    private static final float VICTORY_SPARKLE_RISE = 0.9f;   // 1 描画フレームあたりの上昇量（px）
    private static final float VICTORY_SPARKLE_HEIGHT = 300f; // 舞い上がる高さ範囲（px・天井で巻き戻る）
    private static final float VICTORY_SPARKLE_RADIUS = 4.5f; // 粒の基準半径（px）
    // 残り時間警告（low time・Task 141）。残りが少ないとタイマーを赤く脈動させて緊張感を出す。
    private static final int LOW_TIME_THRESHOLD = 10;     // この秒数以下で警告点滅
    private static final Color LOW_TIME_COLOR = new Color(0.98f, 0.26f, 0.22f, 1f); // 警告の赤
    private static final float LOW_TIME_PULSE_SPEED = 0.35f; // 点滅の角速度
    // ダッシュ残像（motion trail・Task 133）。ダッシュ中のファイターの直近位置にスプライトの寒色ゴーストを
    // 重ね、移動の勢い・残像感を出す純描画演出。位置はファイターの実位置のスナップショット＝乱数なし＝決定的。
    private static final int AFTERIMAGE_MAX = 6;                 // 残像の最大枚数（リングバッファ容量）
    private static final float AFTERIMAGE_ALPHA_MAX = 0.42f;     // 直近（新しい）残像の不透明度
    private static final float AFTERIMAGE_ALPHA_MIN = 0.10f;     // 最古（遠い）残像の不透明度
    private static final Color AFTERIMAGE_TINT = new Color(0.5f, 0.7f, 1f, 1f); // 残像の寒色ティント（乗算）
    // ヒットスパーク（Task 38）：通常ヒット=暖色（白寄りの黄）/ ガード=寒色（青）。放射スポーク数と寸法。
    private static final Color SPARK_HIT_COLOR = new Color(1f, 0.95f, 0.55f, 1f);
    private static final Color SPARK_GUARD_COLOR = new Color(0.60f, 0.85f, 1f, 1f);

    // 着地の砂煙（足元の土埃。Task 131）。複数の小さな丸を左右へ広げ・上昇させ・フェードする純演出。
    private static final Color DUST_COLOR = new Color(0.82f, 0.77f, 0.64f, 0.62f);
    private static final int DUST_PUFFS = 6;          // 土埃の粒数（左右交互）
    private static final float DUST_SPREAD = 30f;      // 横へ広がる最大距離（px）
    private static final float DUST_RISE = 12f;        // 上昇する高さ（px）
    private static final float DUST_PUFF_RADIUS = 7f;  // 粒の基準半径（px）
    // コンボカウンター（Task 39）の文字色（鮮やかなオレンジ）と表示倍率。
    private static final Color COMBO_COLOR = new Color(1f, 0.62f, 0.18f, 1f);
    private static final float COMBO_SCALE = 1.7f;
    private static final float COMBO_PULSE = 0.08f;        // コンボ表示の拡大パルス幅（±・Task 143）
    private static final float COMBO_PULSE_SPEED = 0.4f;   // コンボ表示の拡大パルス角速度（Task 143）
    // ラウンド開始イントロ（Task 42）："ROUND N"=白系 / "FIGHT!"=赤系で開始を強調。
    private static final Color ROUND_INTRO_COLOR = new Color(0.96f, 0.96f, 0.98f, 1f);
    private static final Color FIGHT_FLASH_COLOR = new Color(0.98f, 0.30f, 0.26f, 1f);
    private static final float ROUND_INTRO_ZOOM = 0.82f; // ラウンド開始イントロの寄り倍率（<1 で寄り・Task 138）
    private static final int SPARK_SPOKES = 8;        // 放射スポーク本数
    private static final float SPARK_CORE_RADIUS = 9f; // 中心コア（縮小していく）の初期半径
    private static final float SPARK_REACH = 34f;      // スポーク先端が到達する最大距離
    private static final float SPARK_SPOKE_HALF_WIDTH = 4f; // スポーク基部の半幅
    // ダメージ数値ポップアップ：通常ヒット=暖色（黄）/ ガード chip=寒色（青、GUARD_COLOR と同系）。
    private static final Color POPUP_HIT_COLOR = new Color(1f, 0.92f, 0.40f, 1f);
    private static final Color POPUP_CHIP_COLOR = new Color(0.55f, 0.80f, 1f, 1f);
    private static final float POPUP_RISE_PER_FRAME = 1.3f; // 1 フレームあたりの上昇量（px）
    private static final float POPUP_BASE_OFFSET_Y = 36f;    // 命中位置からの初期持ち上げ（px）
    private static final float POPUP_SCALE = 1.7f;           // 数字フォント倍率（基準）
    private static final float POPUP_MIN_SCALE_FACTOR = 0.75f; // 最小ダメージ時の倍率係数（× POPUP_SCALE・Task 142）
    private static final int POPUP_SCALE_DAMAGE_REF = 180;     // この与ダメージで最大倍率に達する基準（Task 142）
    private static final float POPUP_FADE_START = 0.6f;      // この進捗以降フェード開始（0..1）
    private static final Color WIN_DOT_ON = new Color(1f, 0.85f, 0.20f, 1f);
    private static final Color WIN_DOT_OFF = new Color(0.28f, 0.30f, 0.36f, 1f);
    private static final Color PERFECT_COLOR = new Color(1f, 0.86f, 0.22f, 1f); // PERFECT 演出の金色（Task 127）
    private static final float WIN_DOT_SIZE = 14f;
    private static final float WIN_DOT_GAP = 5f;
    private static final float MARKER_SIZE = 18f;
    private static final float PIP_SIZE = 8f;
    private static final float PIP_GAP = 5f;
    // HP ゲージのレイアウト（HUD 上端）。左右に 1 本ずつ、中央寄せでミラー配置する。
    private static final float HP_BAR_WIDTH = 480f;
    private static final float HP_BAR_HEIGHT = 26f;
    private static final float HP_BAR_MARGIN = 40f;
    private static final float HP_BAR_TOP = 60f;
    private static final float HP_FRAME_THICKNESS = 3f;
    // ガードゲージ（HP バー直下の細バー・Task 43）。HP バーと同じ左右アンカーで減る方向に塗る。
    private static final float GUARD_BAR_HEIGHT = 7f;
    private static final float GUARD_BAR_GAP = 5f; // HP バー枠下端からの隙間
    private static final Color GUARD_BAR_BACK = new Color(0.10f, 0.12f, 0.16f, 1f);
    private static final Color GUARD_BAR_FILL = new Color(0.35f, 0.72f, 1f, 1f);  // 通常＝水色（ガード色と同系）
    private static final Color GUARD_BAR_LOW = new Color(0.98f, 0.55f, 0.20f, 1f); // 残量わずか＝橙で警告
    // スタンゲージ（Task 92）：蓄積で満タンに近づくと黄→赤で警告（めまい間近）。ガードゲージのさらに下に細く配置。
    private static final Color STUN_BAR_BACK = new Color(0.12f, 0.10f, 0.10f, 1f);
    private static final Color STUN_BAR_FILL = new Color(0.95f, 0.82f, 0.30f, 1f);
    private static final Color STUN_BAR_HIGH = new Color(0.95f, 0.35f, 0.25f, 1f); // 満タン間近＝赤（めまい警告）
    private static final Color GUARD_BREAK_COLOR = new Color(1f, 0.30f, 0.26f, 1f); // "GUARD BREAK!" の赤
    private static final float GUARD_BREAK_SCALE = 1.5f;
    // 必殺技ゲージ（スーパーメーター・Task 44）。画面下端の細バー。満タンで EX 可（金色で点灯）。
    private static final float METER_BAR_WIDTH = 360f;
    private static final float METER_BAR_HEIGHT = 12f;
    private static final float METER_BAR_MARGIN = 40f;
    private static final float METER_BAR_BOTTOM = 16f;
    private static final Color METER_BAR_BACK = new Color(0.10f, 0.10f, 0.14f, 1f);
    private static final Color METER_BAR_FILL = new Color(0.30f, 0.62f, 0.95f, 1f);   // 蓄積中＝青
    private static final Color METER_BAR_FULL = new Color(1f, 0.82f, 0.25f, 1f);      // 満タン＝金（EX 可）
    private static final Color METER_FRAME_COLOR = new Color(0.80f, 0.82f, 0.90f, 1f);
    private static final Color EX_PROJECTILE_GLOW = new Color(1f, 0.82f, 0.30f, 1f);  // EX 弾の金グロー
    private static final String STATE_LABEL_GUARD_BREAK = "guard_break"; // 名前下の状態ラベル（ハードコード回避）
    private static final String STATE_LABEL_INVINCIBLE_SUFFIX = " [INV]"; // 無敵フレーム中の付加表示（Task 53）
    private static final String STATE_LABEL_EX_SUFFIX = " [EX]"; // EX 必殺技中の付加表示（Task 54）
    private static final String STATE_LABEL_COUNTER_SUFFIX = " (CH)"; // カウンターヒット被弾の付加表示（Task 71）
    private static final String STATE_LABEL_ARMOR_SUFFIX = " [ARMOR]"; // スーパーアーマー有効中の付加表示（Task 80）
    private static final String STATE_LABEL_JUST_SUFFIX = " [JUST]"; // ジャストガード成立の付加表示（Task 81）
    private static final String STATE_LABEL_SUPER_SUFFIX = " [SUPER]"; // スーパー必殺技中の付加表示（Task 108）
    private static final Color MOVE_LIST_COLOR = new Color(0.95f, 0.95f, 0.78f, 1f); // コマンド表 HUD の文字色（Task 112）
    private static final Color TITLE_ACCENT_COLOR = new Color(0.55f, 0.75f, 1f, 1f); // タイトルロゴの色（Task 116）
    private static final Color CHARSEL_P1_COLOR = new Color(0.40f, 0.80f, 1f, 1f); // キャラ選択 P1（シアン・Task 117）
    private static final Color CHARSEL_P2_COLOR = new Color(1f, 0.62f, 0.30f, 1f); // キャラ選択 P2（橙・Task 117）
    private static final Color INPUT_DISPLAY_COLOR = new Color(0.85f, 0.90f, 0.55f, 0.9f); // 入力表示 HUD の文字色（Task 96）
    private static final float INPUT_DISPLAY_SCALE = 0.9f; // 入力表示 HUD の文字倍率（Task 96）
    private static final String TEXT_GUARD_BREAK = "GUARD BREAK!";        // 頭上のフローティング表示（同上）

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    // キャラのスプライトシート（あれば矩形の代わりにテクスチャ描画。Task 34）。
    private final SpriteLibrary sprites = new SpriteLibrary();
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    // ダメージ数値ポップアップ描画用のフェード色（毎フレームの再確保を避ける作業用バッファ）。
    private final Color popupColor = new Color();
    // ヒットスパーク描画用のフェード色（毎フレームの再確保を避ける作業用バッファ。Task 38）。
    private final Color sparkColor = new Color();
    // ミラーマッチの P2 パレット / 被弾フラッシュ合成用の作業色（毎フレームの再確保を避ける。Task 62）。
    private final Color tintColor = new Color();
    // 足元の影描画用の作業色（毎フレームの再確保を避ける。Task 130）。
    private final Color shadowColor = new Color();
    // 着地の砂煙描画用のフェード色（毎フレームの再確保を避ける。Task 131）。
    private final Color dustColor = new Color();
    // ダッシュ残像（Task 133）：p1=[0] / p2=[1] のダッシュ中の直近位置スナップショットを保持するリングバッファ。
    // ダッシュ中のみ蓄積し、それ以外は空にする＝残像はダッシュの軌跡だけに出る。描画用の作業色も持つ。
    private final GhostTrail[] trails = { new GhostTrail(), new GhostTrail() };
    private final Color afterimageColor = new Color();
    // 飛び道具の軌跡描画用のフェード色（毎フレームの再確保を避ける作業用バッファ。Task 134）。
    private final Color projectileTrailColor = new Color();
    // クリーンヒットの白フラッシュ描画用のフェード色（毎フレームの再確保を避ける作業用バッファ。Task 136）。
    private final Color impactFlashColor = new Color();
    // メーター満タンオーラ（Task 137）：パルス用の描画フレームカウンタと毎フレーム再確保を避ける作業色。
    private int auraTick;
    private final Color auraColor = new Color();
    // 残り時間警告の脈動色（毎フレーム再確保を避ける作業色。Task 141）。
    private final Color timerColor = new Color();
    // HP バーのダメージトレイル（Task 146）：p1=[0]/p2=[1] の遅延ドレイン割合（実 HP 割合へ徐々に追従）。
    private final float[] hpTrail = { 1f, 1f };
    // 空の光の帯 / 低 HP ビネット描画用の作業色（毎フレーム再確保を避ける。Task 145/147）。
    private final Color sweepColor = new Color();
    private final Color vignetteColor = new Color();
    // KO 白フラッシュ（Task 148）：残りフレームと、決着エッジ検出用の前フレーム決着状態。
    private int koFlashFrames;
    private boolean prevConcluded;
    private final Color koFlashColor = new Color();
    private final Color winnerGlowColor = new Color();
    // 画面の微振動（hit shake・Task 132）：残りフレームと振幅。接触時に triggerShake で立ち、毎フレーム減衰する。
    private int shakeFrames;
    private float shakeMagnitude;
    // 現在のステージ色（Task 17）。未設定時はフォールバックを使う。
    private final Color skyTop = new Color(SKY_TOP_FALLBACK);
    private final Color skyBottom = new Color(SKY_BOTTOM_FALLBACK);
    private final Color groundColor = new Color(GROUND_COLOR);
    private String stageName = "";
    // 背景の多層シルエット（Task 151）。setStage で受け取り、パス 1 で空と地面の間に奥から描く。
    private StageLayer[] stageLayers;
    private final Color layerColor = new Color(); // レイヤー描画用の作業色（毎フレーム再確保を避ける）
    private final Color layerHighlight = new Color(); // 前景 frame の内側ハイライト用の作業色（Task 158）

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

    /** 描画に用いるステージ（背景グラデ + 地面色 + 名前）を設定する（Task 17）。 */
    public void setStage(Stage stage) {
        setColor(skyTop, stage.getSkyTop());
        setColor(skyBottom, stage.getSkyBottom());
        setColor(groundColor, stage.getGroundColor());
        stageName = stage.getName();
        stageLayers = stage.getLayers(); // 背景の多層シルエット（任意・null なら従来どおり。Task 151）
    }

    /**
     * 画面の微振動（hit shake）を発動する（Task 132）。接触（打撃 / 飛び道具 / 投げ）時に Core から呼ぶ。
     * 重なって複数回呼ばれたら大きい振幅を採用する（弱い揺れが強い揺れを上書きしない）。純描画演出。
     *
     * @param magnitude 揺れの最大振幅（px）。
     */
    public void triggerShake(float magnitude) {
        shakeMagnitude = Math.max(shakeMagnitude, magnitude);
        shakeFrames = GameConstants.SHAKE_FRAMES;
    }

    /**
     * 画面の微振動を 1 フレーム分カメラへ適用する（Task 132）。残りフレームから決定的に（乱数なし）
     * 振幅を減衰させ、左右はフレームの偶奇・上下は 4 フレーム周期の符号で揺らす。振動が無いときは中心に据える。
     * {@link #renderScene} の {@code camera.update()} 直前に呼ぶ。シミュレーション状態には一切干渉しない。
     */
    private void applyShakeToCamera() {
        float shakeX = 0f, shakeY = 0f;
        if (shakeFrames > 0) {
            float decay = shakeFrames / (float) GameConstants.SHAKE_FRAMES; // 1→0 へ線形減衰
            float amp = shakeMagnitude * decay;
            shakeX = (shakeFrames % 2 == 0) ? amp : -amp;
            shakeY = ((shakeFrames % 4 < 2) ? 1f : -1f) * amp * 0.6f;
            shakeFrames--;
            if (shakeFrames == 0) {
                shakeMagnitude = 0f;
            }
        }
        camera.position.set(GameConstants.WORLD_WIDTH / 2f + shakeX, GameConstants.WORLD_HEIGHT / 2f + shakeY, 0f);
    }

    /** カメラを画面中心へ据える（hit shake のオフセットを持ち越さない。タイトル / キャラ / ステージ選択で呼ぶ。Task 132）。 */
    private void centerCamera() {
        camera.position.set(GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT / 2f, 0f);
        camera.zoom = 1f; // ラウンド開始ズーム（Task 138）を非バトル画面へ持ち越さない。
    }

    /**
     * ラウンド開始イントロのズームイン演出（Task 138）。"ROUND N" → "FIGHT!" の入力ロック中はカメラを
     * わずかに寄せ（{@code zoom < 1}）、イントロ経過に従って通常倍率（1.0）へ戻す＝開始の勢いを出す。
     * イントロ中でなければ常に等倍。撮影モードでは既定でイントロがスキップ（{@code intro=true} 指定時のみ有効）
     * なので、既存スクショレシピは不変（イントロ演出自体と同じ後方互換）。純描画でシミュレーションに非干渉。
     */
    private void applyRoundIntroZoom(RoundManager round) {
        float zoom = 1f;
        int total = round.getIntroTotalFrames();
        if (round.isRoundIntro() && total > 0) {
            // progress：イントロ開始（残り = 総数）で 0、終了直前で 1。総数は RoundManager の実イントロ長を使う
            // （定数決め打ちにせずイントロ長を可変にしても乖離しない）。
            float progress = 1f - round.getIntroCountdown() / (float) total;
            progress = Math.max(0f, Math.min(1f, progress));
            zoom = ROUND_INTRO_ZOOM + (1f - ROUND_INTRO_ZOOM) * progress; // 寄り → 等倍へ
        }
        camera.zoom = zoom;
    }

    private static void setColor(Color target, float[] rgb) {
        target.set(rgb[0], rgb[1], rgb[2], 1f);
    }

    /**
     * バトルシーンを 1 フレーム描画する。
     *
     * @param p1           プレイヤー 1 のファイター（青）
     * @param p2           プレイヤー 2 のファイター（赤）
     * @param anim1        プレイヤー 1 のアニメーション状態
     * @param anim2        プレイヤー 2 のアニメーション状態
     * @param projectiles  飛び道具（必殺技の弾）一覧
     * @param popups       ダメージ数値ポップアップ一覧（被弾 / ガード時の与ダメージ表示）
     * @param sparks       ヒットスパーク一覧（命中位置の火花エフェクト。Task 38）
     * @param round        ラウンド進行 / 勝敗（タイマー・結果表示）
     * @param debug        デバッグ当たり判定オーバーレイ（有効時に判定枠を重ね描き）
     * @param controlsHint 操作ガイド（HUD）
     * @param statusLine   各ファイターの座標 / 向き（HUD・移動の動作確認用）
     */
    public void renderScene(Fighter p1, Fighter p2, FighterAnimator anim1, FighterAnimator anim2,
                            List<Projectile> projectiles, List<DamagePopup> popups, List<HitSpark> sparks,
                            List<LandingDust> dusts, RoundManager round, DebugOverlay debug,
                            String controlsHint, String statusLine,
                            List<String> p1Inputs, boolean moveListVisible) {
        ScreenUtils.clear(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, GameConstants.BG_A);
        // 画面の微振動（hit shake・Task 132）：残りフレームから決定的に（乱数なし）カメラを中心からずらす。
        // 振幅は減衰し、左右上下を符号反転で揺らす。シミュレーションには非干渉＝リプレイ/スクショレシピ不変。
        applyShakeToCamera();
        // ラウンド開始イントロのズームイン演出（Task 138）："ROUND N"/"FIGHT!" 中はカメラを寄せ、開始で通常へ戻す。
        applyRoundIntroZoom(round);
        camera.update();
        // KO 白フラッシュ（Task 148）のエッジ検出：このフレームに KO で決着（戦闘→決着 / ラウンド間）へ
        // 遷移したらフラッシュをアームする。タイムアップ決着では光らせない（KO 限定の余韻演出）。
        boolean concluded = round.isFinished() || round.isBetweenRounds();
        if (concluded && !prevConcluded && round.getReason() == RoundManager.FinishReason.KO) {
            koFlashFrames = KO_FLASH_FRAMES;
        }
        prevConcluded = concluded;
        // キャラのスプライトシートを（未読込なら）読み込む。欠落時は矩形へフォールバック（Task 34）。
        sprites.ensureLoaded(p1.getDef());
        sprites.ensureLoaded(p2.getDef());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // --- パス 1: ステージ背景（空グラデ + 地面）---
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // 空：下端（地平線）→上端のグラデーション。rect(x,y,w,h, c00,c10,c11,c01) は左下→右下→右上→左上。
        shapes.rect(0f, 0f, GameConstants.WORLD_WIDTH, GameConstants.WORLD_HEIGHT,
                skyBottom, skyBottom, skyTop, skyTop);
        // 背景の多層シルエット（Task 151）：空グラデーションの上、地面の前に奥（遠景）→手前（近景）の順で描く。
        // front=false＝背景レイヤーのみ（前景レイヤー＝Task 158 はパス 2.5 でキャラの手前に描く）。
        drawStageLayers(false);
        // 地面（床）。
        shapes.setColor(groundColor);
        shapes.rect(0f, 0f, GameConstants.WORLD_WIDTH, GameConstants.GROUND_Y);
        // 足元の影：キャラスプライト（パス 2）の前に床へ落とし、その上に立つ見栄えにする（Task 130）。
        drawGroundShadow(p1);
        drawGroundShadow(p2);
        // 必殺技ゲージ満タンのオーラ（Task 137）：影と同じくスプライトの前（足元）に金色のパルス光輪を描く。
        // パルス用の描画フレームカウンタ（純描画・乱数なし）。周期 36 の倍数でラップして長時間プレイの int 溢れを防ぐ
        // （位相は保たれる＝見た目に段差なし）。
        auraTick = (auraTick + 1) % 36000;
        drawMeterAura(p1);
        drawMeterAura(p2);
        // 空の光の帯（Task 147）：背景をゆっくり横切る淡い光の縦帯。浮遊粒の前（同じ背景レイヤ）。
        drawSkySweep();
        // 背景の浮遊パーティクル（Task 139）：空気感を出す微かな光の粒。スプライト（パス 2）より後ろ＝背景。
        drawAmbientMotes();
        shapes.end();

        // ミラーマッチ（同キャラ対戦）なら P2 にパレットスワップを適用して識別する（Task 62）。
        // 別キャラ対戦では false ＝従来どおりの見た目（既存スクショ / レシピに回帰しない）。
        boolean mirror = p1.getDef().getId().equals(p2.getDef().getId());

        // --- パス 2: キャラクターのスプライト（テクスチャ描画。Task 34）---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // ダッシュ残像（Task 133）：本体スプライトの前に、ダッシュ軌跡の寒色ゴーストを薄く重ねる。
        updateAndDrawAfterimages(p1, anim1, 0, false);
        drawFighterSprite(p1, anim1, false);
        updateAndDrawAfterimages(p2, anim2, 1, mirror);
        drawFighterSprite(p2, anim2, mirror);
        batch.end();

        // --- パス 2.5: 前景レイヤー（Task 158）---
        // front=true のステージレイヤーをキャラの手前に描いて奥行き（被写界深度）を出す。
        // HP バー等の HUD（パス 3）・デバッグ枠（パス 4）・テキスト（パス 5）はこの後なので前景の上に乗る。
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawStageLayers(true);
        shapes.end();

        // --- パス 3: オーバーレイ（矩形フォールバック / ガード / 攻撃 strike / 接触 / 飛び道具 / HP）---
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawFighterOverlay(p1, anim1, P1_COLOR, false);
        drawFighterOverlay(p2, anim2, P2_COLOR, mirror);
        // 飛び道具（必殺技の弾）。
        drawProjectiles(projectiles);
        // ヒットスパーク（命中位置で拡大＋フェードする火花。Task 38）。
        drawHitSparks(sparks);
        // 着地の砂煙（足元で広がり上昇しフェードする土埃。Task 131）。
        drawLandingDust(dusts);
        // ヒット接触マーカー（active hitbox × 相手 hurtbox が重なるフレームに点灯）。
        drawContactMarker(p1, p2);
        drawContactMarker(p2, p1);
        // HP ゲージ（HUD 上端）。P1 は左から、P2 は右から減る方向に塗る。
        drawHpBar(p1, true);
        drawHpBar(p2, false);
        // ガードゲージ（HP バーの直下の細バー。ガードで減り、尽きるとガードクラッシュ。Task 43）。
        drawGuardGauge(p1, true);
        drawGuardGauge(p2, false);
        // スタンゲージ（ガードゲージの直下。蓄積でめまい＝Task 79。stunThreshold>0 のキャラのみ表示）。Task 92。
        drawStunGauge(p1, true);
        drawStunGauge(p2, false);
        // 必殺技ゲージ（画面下端の細バー。貯まると EX 必殺技が撃てる。Task 44）。
        drawSuperMeter(p1, true);
        drawSuperMeter(p2, false);
        // 勝利ラウンド数を示すドット（HP バー内側端の下）。金色=獲得、暗色=未獲得。
        drawWinDots(round);
        // 低 HP 警告ビネット（Task 145）：どちらかの HP が低いと画面端を赤く脈動させる（戦闘中のみ・決着中は出さない）。
        if (!concluded) {
            drawLowHpVignette(p1, p2);
        }
        // 決着演出（Task 148/149/150）：勝者グロー＋勝利の光の粒＋KO 白フラッシュ（バナーより後ろ＝テキストは最前面）。
        drawRoundEndOverlays(round, p1, p2);
        shapes.end();

        // --- パス 4: デバッグ当たり判定枠（有効時のみ。Line で重ね描き。投影は上で設定済み）---
        debug.drawBoxes(shapes, p1, p2);

        // --- パス 5: テキスト（タイトル / 名前 + アニメ状態ラベル / HP 数値 / 入力 HUD） ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.5f);
        drawCentered(GameConstants.WINDOW_TITLE, GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 30f);
        // ラウンドタイマー（HUD 中央上、HP バー帯の高さ）。残り時間が少ないと赤く脈動して警告する（Task 141）。
        int secs = round.getRemainingSeconds();
        boolean lowTime = !round.isFinished() && !round.isBetweenRounds() && secs <= LOW_TIME_THRESHOLD;
        if (lowTime) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(auraTick * LOW_TIME_PULSE_SPEED);
            timerColor.set(LOW_TIME_COLOR.r, LOW_TIME_COLOR.g, LOW_TIME_COLOR.b, 1f).lerp(Color.WHITE, 1f - pulse);
            font.setColor(timerColor);
            font.getData().setScale(1.8f); // 警告時は少し大きく
        }
        drawCentered(String.valueOf(secs),
                GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - HP_BAR_TOP + 4f);
        if (lowTime) {
            font.setColor(Color.WHITE);
        }
        font.getData().setScale(1.0f);
        drawHpLabels(p1, true);
        drawHpLabels(p2, false);
        drawNameLabel(p1, anim1);
        drawNameLabel(p2, anim2);
        // コンボカウンター（連続ヒット中の相手の頭上に "N HITS!"。Task 39）。
        drawComboCounter(p1);
        drawComboCounter(p2);
        // ガードクラッシュ表示（崩された側の頭上に "GUARD BREAK!"。Task 43）。
        drawGuardBreakLabel(p1);
        drawGuardBreakLabel(p2);
        // ダメージ数値ポップアップ（命中位置から上昇＋終盤フェード。HIT=黄 / CHIP=青）。
        drawDamagePopups(popups);
        if (!stageName.isEmpty()) {
            drawCentered("Stage: " + stageName, GameConstants.WORLD_WIDTH / 2f, 100f);
        }
        drawCentered(controlsHint, GameConstants.WORLD_WIDTH / 2f, 70f);
        drawCentered(statusLine, GameConstants.WORLD_WIDTH / 2f, 40f);
        drawInputDisplay(p1Inputs); // P1 入力表示 HUD（Task 96）
        if (moveListVisible) {
            drawMoveList(p1, p2); // コマンド表 HUD（技/コマンド一覧・F5・Task 112）
        }

        if (debug.isEnabled()) {
            drawCentered("DEBUG: push(blue) hurt(green) hit(red)", GameConstants.WORLD_WIDTH / 2f, 120f);
        }
        if (round.isFinished()) {
            drawResultBanner(p1, p2, round);
        } else if (round.isBetweenRounds()) {
            drawBetweenRoundBanner(p1, p2, round);
        } else if (round.isRoundIntro()) {
            drawRoundIntroBanner(round);
        }
        batch.end();
    }

    /** 勝利ラウンド数ドット（HP バー内側端の直下）。P1 は左バー右端から右へ、P2 は右バー左端から左へ並べる。 */
    private void drawWinDots(RoundManager round) {
        int rtw = round.getRoundsToWin();
        float barBottom = GameConstants.WORLD_HEIGHT - HP_BAR_TOP - HP_BAR_HEIGHT;
        float dotY = barBottom - WIN_DOT_SIZE - 4f;
        float p1BarRight = HP_BAR_MARGIN + HP_BAR_WIDTH;
        float p2BarLeft = GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        for (int i = 0; i < rtw; i++) {
            shapes.setColor(i < round.getP1Wins() ? WIN_DOT_ON : WIN_DOT_OFF);
            shapes.rect(p1BarRight + 8f + i * (WIN_DOT_SIZE + WIN_DOT_GAP), dotY, WIN_DOT_SIZE, WIN_DOT_SIZE);
        }
        for (int i = 0; i < rtw; i++) {
            shapes.setColor(i < round.getP2Wins() ? WIN_DOT_ON : WIN_DOT_OFF);
            float x = p2BarLeft - 8f - (i + 1) * WIN_DOT_SIZE - i * WIN_DOT_GAP;
            shapes.rect(x, dotY, WIN_DOT_SIZE, WIN_DOT_SIZE);
        }
    }

    /** ラウンド間バナー：決着理由 + ラウンド勝者 + 次ラウンド開始カウントダウン。 */
    private void drawBetweenRoundBanner(Fighter p1, Fighter p2, RoundManager round) {
        String reason = round.getReason() == RoundManager.FinishReason.KO ? "K.O." : "TIME UP";
        String result;
        switch (round.getRoundWinner()) {
            case P1:
                result = p1.getDef().getName() + " WINS";
                break;
            case P2:
                result = p2.getDef().getName() + " WINS";
                break;
            default:
                result = "DRAW";
                break;
        }
        int secsLeft = (round.getBetweenCountdown() + GameConstants.TARGET_FPS - 1) / GameConstants.TARGET_FPS;
        float cx = GameConstants.WORLD_WIDTH / 2f;
        // PERFECT（ノーダメージ勝利・Task 127）：金色で決着理由の上に強調表示する。
        if (round.isRoundPerfect()) {
            font.setColor(PERFECT_COLOR);
            font.getData().setScale(2.0f);
            drawCentered("PERFECT!", cx, GameConstants.WORLD_HEIGHT / 2f + 95f);
            font.setColor(Color.WHITE);
        }
        font.getData().setScale(2.5f);
        drawCentered(reason, cx, GameConstants.WORLD_HEIGHT / 2f + 50f);
        font.getData().setScale(1.8f);
        drawCentered(result, cx, GameConstants.WORLD_HEIGHT / 2f + 5f);
        font.getData().setScale(1.2f);
        drawCentered("ROUND " + (round.getCurrentRound() + 1) + "  in " + secsLeft,
                cx, GameConstants.WORLD_HEIGHT / 2f - 35f);
        font.getData().setScale(1.0f);
    }

    /**
     * ラウンド開始イントロバナー（Task 42）：開始前半は "ROUND N"（白）、末尾 {@code FIGHT_FLASH_FRAMES}
     * フレームは "FIGHT!"（赤・拡大）を画面中央に表示する。この間は戦闘・タイマーが停止している。
     * 色・倍率は共有状態のため、描画後に白・等倍へ戻す。
     */
    private void drawRoundIntroBanner(RoundManager round) {
        float cx = GameConstants.WORLD_WIDTH / 2f;
        float cy = GameConstants.WORLD_HEIGHT / 2f + 20f;
        if (round.isFightFlash()) {
            font.setColor(FIGHT_FLASH_COLOR);
            font.getData().setScale(3.0f);
            drawCentered("FIGHT!", cx, cy);
        } else {
            font.setColor(ROUND_INTRO_COLOR);
            font.getData().setScale(2.5f);
            drawCentered("ROUND " + round.getCurrentRound(), cx, cy);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
    }

    /** マッチ決着時のバナー（決着理由 + マッチ勝者 + スコア）を画面中央に大きく描く。 */
    private void drawResultBanner(Fighter p1, Fighter p2, RoundManager round) {
        String reason = round.getReason() == RoundManager.FinishReason.KO ? "K.O." : "TIME UP";
        String result;
        switch (round.getMatchWinner()) {
            case P1:
                result = p1.getDef().getName() + " WINS!";
                break;
            case P2:
                result = p2.getDef().getName() + " WINS!";
                break;
            default:
                result = "DRAW";
                break;
        }
        String score = round.getP1Wins() + " - " + round.getP2Wins();
        float cx = GameConstants.WORLD_WIDTH / 2f;
        // PERFECT（最終ラウンドをノーダメージで決めた場合・Task 127）：金色で決着理由の上に強調表示する。
        if (round.isRoundPerfect()) {
            font.setColor(PERFECT_COLOR);
            font.getData().setScale(2.2f);
            drawCentered("PERFECT!", cx, GameConstants.WORLD_HEIGHT / 2f + 100f);
            font.setColor(Color.WHITE);
        }
        font.getData().setScale(3.0f);
        drawCentered(reason, cx, GameConstants.WORLD_HEIGHT / 2f + 50f);
        font.getData().setScale(2.0f);
        drawCentered(result, cx, GameConstants.WORLD_HEIGHT / 2f);
        font.getData().setScale(1.5f);
        drawCentered(score, cx, GameConstants.WORLD_HEIGHT / 2f - 45f);
        font.getData().setScale(1.0f);
    }

    /**
     * HP ゲージを 1 本描く。{@code leftAnchored} の側（P1=左 / P2=右）に枠を固定し、減少分は
     * 中央側から減る方向に塗る（対戦ゲームの定番配置）。色は残量に応じて緑→黄→赤へ変える。
     */
    private void drawHpBar(Fighter f, boolean leftAnchored) {
        float top = GameConstants.WORLD_HEIGHT - HP_BAR_TOP;
        float barBottom = top - HP_BAR_HEIGHT;
        float outerLeft = leftAnchored
                ? HP_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        // 枠（縁取り）→ 背景 → 残量フィルの順で重ねる。
        shapes.setColor(HP_FRAME_COLOR);
        shapes.rect(outerLeft - HP_FRAME_THICKNESS, barBottom - HP_FRAME_THICKNESS,
                HP_BAR_WIDTH + HP_FRAME_THICKNESS * 2f, HP_BAR_HEIGHT + HP_FRAME_THICKNESS * 2f);
        shapes.setColor(HP_BACK_COLOR);
        shapes.rect(outerLeft, barBottom, HP_BAR_WIDTH, HP_BAR_HEIGHT);
        float ratio = f.getHpRatio();
        float fillWidth = HP_BAR_WIDTH * ratio;
        // 減少は中央側から：左アンカーは左端固定で右が縮み、右アンカーは右端固定で左が縮む。
        float fillLeft = leftAnchored ? outerLeft : outerLeft + (HP_BAR_WIDTH - fillWidth);
        // ダメージトレイル（Task 146）：遅延割合 hpTrail を実 HP 割合へ徐々に詰め、減った差分を明るい黄で残す。
        // 回復 / ラウンド開始（割合が増加）では即追従。被弾（減少）でのみ遅れて、減った量が一瞬尾を引いて見える。
        int idx = leftAnchored ? 0 : 1;
        if (ratio >= hpTrail[idx]) {
            hpTrail[idx] = ratio;
        } else {
            hpTrail[idx] = Math.max(ratio, hpTrail[idx] - HP_TRAIL_DRAIN);
        }
        if (hpTrail[idx] > ratio) {
            float trailWidth = HP_BAR_WIDTH * (hpTrail[idx] - ratio);
            // 失った側（左アンカー＝白フィルの右隣／右アンカー＝白フィルの左隣）に明るい遅延バーを置く。
            float trailLeft = leftAnchored ? fillLeft + fillWidth : fillLeft - trailWidth;
            shapes.setColor(HP_TRAIL_COLOR);
            shapes.rect(trailLeft, barBottom, trailWidth, HP_BAR_HEIGHT);
        }
        // 回復可能ダメージ（レッドライフ・Task 104）：白 HP の減った側に隣接して赤ゲージを描く（無被弾で白へ戻る分）。
        // 白 HP の上から赤を描く前に背景の上へ赤を置くため、白フィルの前に描画する。
        float recoverWidth = HP_BAR_WIDTH * f.getRecoverableRatio();
        if (recoverWidth > 0f) {
            // 左アンカーは白フィルの右隣、右アンカーは白フィルの左隣に赤を配置（失った位置に重なる）。
            float recoverLeft = leftAnchored ? fillLeft + fillWidth : fillLeft - recoverWidth;
            shapes.setColor(HP_RECOVERABLE_COLOR);
            shapes.rect(recoverLeft, barBottom, recoverWidth, HP_BAR_HEIGHT);
        }
        shapes.setColor(hpFillColor(ratio));
        shapes.rect(fillLeft, barBottom, fillWidth, HP_BAR_HEIGHT);
    }

    /**
     * ガードゲージを 1 本描く（Task 43）。HP バーの直下に細く配置し、HP と同じ左右アンカーで
     * 中央側から減る方向に塗る。残量が少ないと橙で警告する。ガードクラッシュの予兆を可視化する。
     */
    private void drawGuardGauge(Fighter f, boolean leftAnchored) {
        float top = GameConstants.WORLD_HEIGHT - HP_BAR_TOP;
        float barBottom = top - HP_BAR_HEIGHT;
        float gaugeTop = barBottom - HP_FRAME_THICKNESS - GUARD_BAR_GAP;
        float gaugeBottom = gaugeTop - GUARD_BAR_HEIGHT;
        float outerLeft = leftAnchored
                ? HP_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        shapes.setColor(GUARD_BAR_BACK);
        shapes.rect(outerLeft, gaugeBottom, HP_BAR_WIDTH, GUARD_BAR_HEIGHT);
        float ratio = Math.max(0f, Math.min(1f, f.getGuardGauge() / GameConstants.GUARD_GAUGE_MAX));
        float fillWidth = HP_BAR_WIDTH * ratio;
        float fillLeft = leftAnchored ? outerLeft : outerLeft + (HP_BAR_WIDTH - fillWidth);
        shapes.setColor(ratio <= 0.30f ? GUARD_BAR_LOW : GUARD_BAR_FILL);
        shapes.rect(fillLeft, gaugeBottom, fillWidth, GUARD_BAR_HEIGHT);
    }

    /**
     * スタンゲージを 1 本描く（Task 92）。ガードゲージのさらに下に細く配置し、被弾で増える蓄積スタン値を表示する。
     * {@code stunThreshold <= 0}（めまい無効）のキャラは描かない。満タンに近づくと黄→赤で「めまい間近」を警告する。
     */
    private void drawStunGauge(Fighter f, boolean leftAnchored) {
        int threshold = f.getDef().getStunThreshold();
        if (threshold <= 0) {
            return; // めまい無効のキャラはスタンゲージなし（従来表示を変えない）
        }
        float top = GameConstants.WORLD_HEIGHT - HP_BAR_TOP;
        float barBottom = top - HP_BAR_HEIGHT;
        // ガードゲージの 1 段下に積む（ガード = barBottom - gap - height、スタン = その下 - 小隙間 - height）。
        float guardBottom = barBottom - HP_FRAME_THICKNESS - GUARD_BAR_GAP - GUARD_BAR_HEIGHT;
        float gaugeBottom = guardBottom - 3f - GUARD_BAR_HEIGHT;
        float outerLeft = leftAnchored
                ? HP_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        shapes.setColor(STUN_BAR_BACK);
        shapes.rect(outerLeft, gaugeBottom, HP_BAR_WIDTH, GUARD_BAR_HEIGHT);
        float ratio = Math.max(0f, Math.min(1f, f.getStunMeter() / (float) threshold));
        float fillWidth = HP_BAR_WIDTH * ratio;
        float fillLeft = leftAnchored ? outerLeft : outerLeft + (HP_BAR_WIDTH - fillWidth);
        shapes.setColor(ratio >= 0.75f ? STUN_BAR_HIGH : STUN_BAR_FILL);
        shapes.rect(fillLeft, gaugeBottom, fillWidth, GUARD_BAR_HEIGHT);
    }

    /**
     * 必殺技ゲージ（スーパーメーター）を 1 本描く（Task 44）。画面下端に配置し、HP/ガードと同じく
     * P1 は左から / P2 は右から増える方向に塗る。満タンは金色（EX 必殺技が撃てる合図）。
     */
    private void drawSuperMeter(Fighter f, boolean leftAnchored) {
        float outerLeft = leftAnchored
                ? METER_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - METER_BAR_MARGIN - METER_BAR_WIDTH;
        shapes.setColor(METER_FRAME_COLOR);
        shapes.rect(outerLeft - 2f, METER_BAR_BOTTOM - 2f, METER_BAR_WIDTH + 4f, METER_BAR_HEIGHT + 4f);
        shapes.setColor(METER_BAR_BACK);
        shapes.rect(outerLeft, METER_BAR_BOTTOM, METER_BAR_WIDTH, METER_BAR_HEIGHT);
        float ratio = Math.max(0f, Math.min(1f, f.getSuperMeter() / GameConstants.SUPER_METER_MAX));
        float fillWidth = METER_BAR_WIDTH * ratio;
        float fillLeft = leftAnchored ? outerLeft : outerLeft + (METER_BAR_WIDTH - fillWidth);
        shapes.setColor(f.hasFullMeter() ? METER_BAR_FULL : METER_BAR_FILL);
        shapes.rect(fillLeft, METER_BAR_BOTTOM, fillWidth, METER_BAR_HEIGHT);
    }

    /** 残量割合に応じた HP フィル色（高=緑 / 中=黄 / 低=赤）。 */
    private static Color hpFillColor(float ratio) {
        if (ratio > 0.5f) {
            return HP_FILL_HIGH;
        }
        return ratio > 0.25f ? HP_FILL_MID : HP_FILL_LOW;
    }

    /** HP ゲージに重ねる名前（外側寄せ）と HP 数値（内側寄せ）のラベル。 */
    private void drawHpLabels(Fighter f, boolean leftAnchored) {
        float top = GameConstants.WORLD_HEIGHT - HP_BAR_TOP;
        float labelY = top + 18f;
        float outerLeft = leftAnchored
                ? HP_BAR_MARGIN
                : GameConstants.WORLD_WIDTH - HP_BAR_MARGIN - HP_BAR_WIDTH;
        String hp = f.getCurrentHp() + " / " + f.getMaxHp();
        if (leftAnchored) {
            font.draw(batch, f.getDef().getName(), outerLeft, labelY);
            layout.setText(font, hp);
            font.draw(batch, layout, outerLeft + HP_BAR_WIDTH - layout.width, labelY);
        } else {
            layout.setText(font, f.getDef().getName());
            font.draw(batch, layout, outerLeft + HP_BAR_WIDTH - layout.width, labelY);
            font.draw(batch, hp, outerLeft, labelY);
        }
    }

    /**
     * ファイターのスプライト（テクスチャ）をパス 2（{@link SpriteBatch}）で描く（Task 34）。
     *
     * <p>JSON にスプライト定義があり読み込み済みのキャラのみ描画する（未指定 / 欠落キャラはここでは
     * 何もせず、パス 3 の {@link #drawFighterOverlay} が矩形フォールバックを描く）。アニメーション状態
     * （→行）・フレーム（→列）に対応する領域を引き、向きが左なら水平反転、しゃがみ中は高さを縮め、
     * のけぞり中は赤みを乗せる。位置は矩形版と同じく中心 X 基準・足元 Y + 縦ボブ。
     */
    private void drawFighterSprite(Fighter f, FighterAnimator anim, boolean paletteSwap) {
        Character d = f.getDef();
        TextureRegion region = sprites.region(d, anim.getState(), anim.getFrameIndex());
        if (region == null) {
            return; // スプライト未指定 / 欠落 → 矩形フォールバック（drawFighterOverlay）。
        }
        float left = f.getX() - d.getWidth() / 2f;
        float bottom = f.getY() + anim.bobOffset();
        float drawHeight = f.isCrouching() ? d.getHeight() / 3f : d.getHeight();
        // 向き：シートは右向きが基準。左向きは水平反転（共有領域のため毎回 flip 状態を揃える）。
        boolean faceLeft = !f.isFacingRight();
        if (region.isFlipX() != faceLeft) {
            region.flip(true, false);
        }
        // 描画色：ミラーマッチ P2 のパレットスワップ（乗算）＋ のけぞり中の赤み（被弾フラッシュ）を合成する。
        // 既定（非ミラー・非のけぞり）は白＝無加工で従来どおり。
        boolean tinted = paletteSwap || f.isInHitstun();
        if (tinted) {
            tintColor.set(Color.WHITE);
            if (paletteSwap) {
                tintColor.mul(MIRROR_P2_TINT);
            }
            if (f.isInHitstun()) {
                tintColor.mul(1f, 0.55f, 0.55f, 1f); // 矩形版の hitstunFlash に相当
            }
            batch.setColor(tintColor);
        }
        batch.draw(region, left, bottom, d.getWidth(), drawHeight);
        if (tinted) {
            batch.setColor(Color.WHITE);
        }
        // クリーンヒットの白フラッシュ（Task 136）：被弾直後、スプライトを加算合成でもう一度描いて
        // シルエットを発光させる（矩形オーバーレイと違い透明余白を光らせない＝箱に見えない）。強さは
        // 残りフレーム比で減衰。加算ブレンドに切り替え、描画後に既定（通常 α ブレンド）へ戻す。
        int flash = f.getImpactFlashFrames();
        if (flash > 0) {
            float k = IMPACT_FLASH_PEAK * (flash / (float) GameConstants.IMPACT_FLASH_FRAMES);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // 加算合成（発光）
            batch.setColor(k, k, k, 1f);
            batch.draw(region, left, bottom, d.getWidth(), drawHeight);
            batch.setColor(Color.WHITE);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // 既定の α ブレンドへ戻す
        }
    }

    /**
     * ダッシュ残像（motion trail）を更新・描画する（純描画演出。Task 133）。
     *
     * <p>ファイターがダッシュ中（{@link Fighter#isDashing()}＝地上ステップ / バックステップ / 空中ダッシュ）
     * のときだけ、リングバッファに溜めた「過去フレームの実位置」をスプライトの寒色ゴーストとして本体の
     * 後ろに薄く重ね、移動の勢い・残像感を出す。古い残像ほど薄く（{@link #AFTERIMAGE_ALPHA_MIN}）・
     * 直近ほど濃く（{@link #AFTERIMAGE_ALPHA_MAX}）描く。ダッシュ中でなければバッファを空にする＝残像は
     * ダッシュの軌跡にだけ出る。スナップショットはファイターの実位置（乱数なし）なので決定的で、シミュレーション
     * 状態・当たり判定・リプレイには一切干渉しない（描画後に最新位置を追加し、本体スプライトと重複させない）。
     *
     * @param f           対象ファイター
     * @param anim        そのファイターのアニメーション状態（行 / フレーム / 縦ボブ）
     * @param index       バッファのインデックス（p1=0 / p2=1）
     * @param paletteSwap ミラーマッチ P2 のパレットスワップ（残像にも乗算して識別を保つ。Task 62）
     */
    private void updateAndDrawAfterimages(Fighter f, FighterAnimator anim, int index, boolean paletteSwap) {
        GhostTrail trail = trails[index];
        if (!f.isDashing()) {
            trail.clear(); // ダッシュ終了で軌跡を消す（次のダッシュまで残像なし）。
            return;
        }
        Character d = f.getDef();
        // 過去フレームの残像を「最古→最新」の順に、薄→濃のフェードで本体の後ろに描く。
        for (int j = 0; j < trail.size; j++) {
            int slot = (trail.head - trail.size + j + AFTERIMAGE_MAX) % AFTERIMAGE_MAX;
            // size==1 のときは最新扱い（MAX 濃度）。複数あれば最古=MIN→最新=MAX で線形補間。
            float t = trail.size > 1 ? j / (float) (trail.size - 1) : 1f;
            float alpha = AFTERIMAGE_ALPHA_MIN + (AFTERIMAGE_ALPHA_MAX - AFTERIMAGE_ALPHA_MIN) * t;
            drawGhost(d, trail.x[slot], trail.y[slot], trail.state[slot], trail.frame[slot],
                    trail.faceLeft[slot], trail.crouch[slot], alpha, paletteSwap);
        }
        // このフレームの実位置を軌跡に追加（次フレーム以降の残像になる）。本体スプライトはこの後に描かれる。
        trail.push(f.getX(), f.getY() + anim.bobOffset(), anim.getState(), anim.getFrameIndex(),
                !f.isFacingRight(), f.isCrouching());
    }

    /**
     * 残像ゴースト 1 枚を描く（Task 133）。{@link #drawFighterSprite} と同じスプライト領域・向き反転・しゃがみ
     * 高さ圧縮を用いるが、被弾フラッシュは乗せず、寒色ティント（{@link #AFTERIMAGE_TINT}）に指定の不透明度を
     * 掛けて半透明のゴーストにする。スプライト未指定 / 欠落のキャラは残像を出さない（{@code region==null}）。
     */
    private void drawGhost(Character d, float gx, float gy, AnimationState state, int frameIndex,
                           boolean faceLeft, boolean crouch, float alpha, boolean paletteSwap) {
        TextureRegion region = sprites.region(d, state, frameIndex);
        if (region == null) {
            return; // スプライト未指定 / 欠落キャラは残像なし（後方互換）。
        }
        float left = gx - d.getWidth() / 2f;
        float drawHeight = crouch ? d.getHeight() / 3f : d.getHeight();
        if (region.isFlipX() != faceLeft) {
            region.flip(true, false);
        }
        afterimageColor.set(AFTERIMAGE_TINT);
        if (paletteSwap) {
            afterimageColor.mul(MIRROR_P2_TINT);
        }
        afterimageColor.a = alpha;
        batch.setColor(afterimageColor);
        batch.draw(region, left, gy, d.getWidth(), drawHeight);
        batch.setColor(Color.WHITE);
    }

    /**
     * ファイターの足元に楕円の影を落とす（純描画演出。Task 130）。
     *
     * <p>影は常に床（{@link GameConstants#GROUND_Y}）上に置き、滞空高さ（{@code getY() - GROUND_Y}）に
     * 応じて横径・縦径・不透明度を一様に縮める＝高く跳ぶほど小さく薄くなり、奥行き感を出す。
     * 背景 / 床と同じパス 1（{@link ShapeRenderer.ShapeType#Filled}）内で、スプライト描画（パス 2）より
     * 前に呼ぶことでキャラが影の上に立つ。乱数なし・戦闘ロジックや位置に干渉しない純粋な演出。
     */
    private void drawGroundShadow(Fighter f) {
        Character d = f.getDef();
        float air = Math.max(0f, f.getY() - GameConstants.GROUND_Y);
        float scale = Math.max(SHADOW_MIN_SCALE, SHADOW_AIR_FALLOFF / (SHADOW_AIR_FALLOFF + air));
        float w = d.getWidth() * SHADOW_WIDTH_SCALE * scale;
        float h = SHADOW_HEIGHT * scale;
        shadowColor.set(SHADOW_COLOR);
        shadowColor.a = SHADOW_COLOR.a * scale;
        shapes.setColor(shadowColor);
        shapes.ellipse(f.getX() - w / 2f, GameConstants.GROUND_Y - h / 2f, w, h);
    }

    /**
     * 必殺技ゲージが満タンのファイターの足元に、金色のパルスする光輪を描く（純描画演出。Task 137）。
     *
     * <p>EX 必殺技 / スーパー必殺技が撃てる合図として、足元（床）に金色の楕円を出し、{@link #auraTick} を
     * 用いて横径・不透明度を周期的に脈動させる（乱数なし＝決定的）。影（{@link #drawGroundShadow}）と同じく
     * スプライト描画（パス 2）の前に呼び、キャラがオーラの上に立つ見栄えにする。満タンでなければ何も描かない
     * ＝従来どおり（後方互換）。HUD のメーターバー（{@link #drawSuperMeter}）と連動した視認性の高い表現。
     */
    private void drawMeterAura(Fighter f) {
        if (!f.hasFullMeter()) {
            return; // 満タンのときだけ点灯（貯まっていなければ従来どおり何も出さない）。
        }
        Character d = f.getDef();
        // パルス：周期 METER_AURA_PERIOD で 1±METER_AURA_PULSE を行き来する（sin・乱数なし）。
        float pulse = 1f + METER_AURA_PULSE * (float) Math.sin((auraTick % METER_AURA_PERIOD) / METER_AURA_PERIOD * Math.PI * 2.0);
        float w = d.getWidth() * METER_AURA_WIDTH_SCALE * pulse;
        float h = METER_AURA_HEIGHT * pulse;
        auraColor.set(METER_AURA_COLOR);
        auraColor.a = METER_AURA_COLOR.a * pulse;
        shapes.setColor(auraColor);
        shapes.ellipse(f.getX() - w / 2f, GameConstants.GROUND_Y - h / 2f, w, h);
    }

    /**
     * 背景の浮遊パーティクル（ambient motes）を描く（純描画演出。Task 139）。空気感・奥行きを出すため、
     * 空（地面より上）の領域に微かな光の粒をゆっくり上昇・横揺れさせる。各粒の位置は粒番号と描画フレーム
     * カウンタ（{@link #auraTick}）から決まる固定計算＝**乱数なし＝決定的**（入力リプレイと両立）。
     * スプライト描画（パス 2）の前に呼ぶ＝キャラの後ろの背景レイヤ。位置・当たり判定には一切干渉しない。
     */
    private void drawAmbientMotes() {
        float top = GameConstants.WORLD_HEIGHT;
        float bottom = GameConstants.GROUND_Y + 20f; // 地面のすぐ上から
        float span = top - bottom;
        if (span <= 0f) {
            return;
        }
        shapes.setColor(MOTE_COLOR);
        for (int i = 0; i < MOTE_COUNT; i++) {
            // 横位置：粒ごとに散らした基準 X に sin の横揺れを足す（粒番号で位相をずらす）。
            float baseX = (i * 977) % (int) GameConstants.WORLD_WIDTH;
            float x = baseX + MOTE_DRIFT * (float) Math.sin(auraTick * MOTE_DRIFT_SPEED + i * 1.3f);
            // 縦位置：粒ごとの初期高さ＋上昇量を span で wrap（下から上へ流れて天井で巻き戻る）。
            float rise = (i * 137f + auraTick * MOTE_RISE) % span;
            float y = bottom + rise;
            // 半径：粒番号でわずかに変える（一様にしない）。
            float r = MOTE_RADIUS * (0.7f + 0.3f * (float) Math.sin(i * 2.1f));
            shapes.circle(x, y, r);
        }
    }

    /**
     * 背景の多層シルエット（Task 151）を奥（遠景）→手前（近景）の順に描く。{@link Stage#getLayers()} の各レイヤーを
     * 形状（band/buildings/peaks/hills/pillars）に応じてシルエット描画する。すべて要素番号と {@link #auraTick}（任意の
     * ドリフト）からの固定計算＝**乱数なし＝決定的**。レイヤー未指定（null）なら何も描かず従来どおり（後方互換）。
     * パス 1（空グラデーションの後・地面の前）で呼ぶ＝奥行きのある背景レイヤ。
     */
    /**
     * ステージの多層シルエットを描く（Task 151）。{@code front=false} で背景（空と地面の間・キャラの後ろ）、
     * {@code front=true} で前景（キャラの手前・Task 158）のレイヤーだけを描く。レイヤーの {@code isFront()} で振り分ける。
     */
    private void drawStageLayers(boolean front) {
        if (stageLayers == null) {
            return;
        }
        float w = GameConstants.WORLD_WIDTH;
        for (StageLayer layer : stageLayers) {
            if (layer == null) {
                continue;
            }
            if (layer.isFront() != front) {
                continue; // 当該パス（背景/前景）のレイヤーのみ描く
            }
            float[] c = layer.getColor();
            if (c == null || c.length < 3) {
                continue; // 色未指定のレイヤーはスキップ
            }
            layerColor.set(c[0], c[1], c[2], layer.getAlpha());
            shapes.setColor(layerColor);
            float baseY = layer.getBaseY();
            float h = layer.getHeight();
            int count = layer.getCount();
            float spacing = w / count;
            // ドリフト：要素間隔で wrap させて端で途切れないようタイル状に流す（雲・もや等の演出。0 で静止）。
            float phase = layer.getDrift() != 0f ? (layer.getDrift() * auraTick) % spacing : 0f;
            switch (layer.getShape()) {
                case "buildings": drawLayerBuildings(baseY, h, count, spacing, phase); break;
                case "peaks":     drawLayerPeaks(baseY, h, count, spacing, phase); break;
                case "hills":     drawLayerHills(baseY, h, w); break;
                case "pillars":   drawLayerPillars(baseY, h, count, spacing, phase); break;
                case "clouds":    drawLayerClouds(baseY, h, count, w, layer.getDrift()); break;
                case "snow":      drawLayerSnow(baseY, h, count, w, layer.getDrift()); break;
                case "embers":    drawLayerEmbers(baseY, h, count, w, layer.getDrift()); break;
                case "frame":     drawLayerFrame(baseY, h, w); break;
                case "band":
                default:          shapes.rect(0f, baseY, w, h); break; // 帯（遠景の地形/水平線）・未対応も帯に
            }
        }
    }

    /** 都市のスカイライン（高さの違う矩形ビル群）。要素番号からの sin で高さ/幅を決定的に散らす。Task 151。 */
    private void drawLayerBuildings(float baseY, float h, int count, float spacing, float phase) {
        for (int i = -1; i <= count; i++) {
            float x = i * spacing + phase;
            float bw = spacing * (0.62f + 0.22f * Math.abs((float) Math.sin(i * 1.3f)));
            float bh = h * (0.42f + 0.58f * Math.abs((float) Math.sin(i * 1.7f + 0.5f)));
            shapes.rect(x + (spacing - bw) / 2f, baseY, bw, bh);
        }
    }

    /** 遠景の山並み（三角形のピーク群）。Task 151。 */
    private void drawLayerPeaks(float baseY, float h, int count, float spacing, float phase) {
        for (int i = -1; i <= count; i++) {
            float cx = i * spacing + phase + spacing / 2f;
            float pw = spacing * (1.0f + 0.35f * (float) Math.sin(i * 0.9f));
            float ph = h * (0.5f + 0.5f * Math.abs((float) Math.sin(i * 1.27f)));
            shapes.triangle(cx - pw / 2f, baseY, cx + pw / 2f, baseY, cx, baseY + ph);
        }
    }

    /** なだらかな丘のシルエット（sin 曲線の下を細い矩形で塗る）。Task 151。 */
    private void drawLayerHills(float baseY, float h, float w) {
        float step = 8f;
        for (float x = 0f; x < w; x += step) {
            float hy = h * (0.55f + 0.45f * (float) Math.sin(x * 0.006f));
            shapes.rect(x, baseY, step + 1f, hy);
        }
    }

    /**
     * たなびく雲（soft puffs）。各雲は重なる円のクラスタで、横位置を `drift × auraTick` で流し画面幅で wrap させる
     * （Task 155）。円は本環境のソフトウェア GL でも正常にブレンドされる（全画面ソリッド rect の罠を避ける）。
     * y は雲番号から決定的に散らす。空のあるステージを生き生きとさせる純演出（乱数なし）。
     */
    private void drawLayerClouds(float baseY, float h, int count, float w, float drift) {
        float wrap = w + 240f; // 画面外マージン込みで wrap（端で途切れない）
        for (int i = 0; i < count; i++) {
            float x0 = (i * (wrap / count) + drift * auraTick) % wrap;
            if (x0 < 0f) {
                x0 += wrap;
            }
            float x = x0 - 120f;
            float y = baseY + h * (0.2f + 0.6f * Math.abs((float) Math.sin(i * 1.9f)));
            float s = 12f + 8f * Math.abs((float) Math.sin(i * 2.3f)); // 雲のスケール
            // 重なる円で 1 つの雲（中央大・左右小）。
            shapes.circle(x, y, s);
            shapes.circle(x - s * 0.9f, y - s * 0.15f, s * 0.66f);
            shapes.circle(x + s * 0.9f, y - s * 0.1f, s * 0.72f);
            shapes.circle(x + s * 0.3f, y + s * 0.35f, s * 0.6f);
            shapes.circle(x - s * 0.4f, y + s * 0.3f, s * 0.55f);
        }
    }

    /**
     * 降る情景（雪・桜の花びら）。Task 156。`count` 個の粒を上端から `baseY..baseY+h` の帯へ落とし、
     * 落下位置は `auraTick` で循環させ画面端で wrap させる（横位置は `sin` で左右に揺らぐ＝雪/花びらの漂い）。
     * 落下速度は `drift` を基準に粒ごとに散らす。色は JSON 指定（白＝雪／桜色＝花びら）。
     * 円で描く（ソフトウェア GL でも正常合成＝全画面ソリッド rect の罠を回避）。乱数なし・決定的。
     */
    private void drawLayerSnow(float baseY, float h, int count, float w, float drift) {
        float span = h <= 0f ? GameConstants.WORLD_HEIGHT : h; // 落下帯の高さ
        float top = baseY + span;
        float baseSpeed = drift <= 0f ? 0.6f : drift; // 落下基準速度（px/frame）
        for (int i = 0; i < count; i++) {
            float speed = baseSpeed * (0.6f + 0.5f * Math.abs((float) Math.sin(i * 1.7f)));
            // 上端 top から下へ落ち span で wrap（粒ごとに位相をずらす）。
            float fall = (auraTick * speed + i * (span / count)) % span;
            float y = top - fall;
            // 横位置：均等配置＋ゆっくりした左右の揺らぎ。
            float sway = 18f * (float) Math.sin(auraTick * 0.03f + i * 1.3f);
            float x0 = (i * (w / count) + sway) % w;
            if (x0 < 0f) {
                x0 += w;
            }
            float r = 2.5f + 2f * Math.abs((float) Math.sin(i * 2.1f)); // 粒のサイズ差
            shapes.circle(x0, y, r);
        }
    }

    /**
     * 立ち昇る火の粉（embers）。Task 157。`snow`（落下）と対で、粒を `baseY` から上へ昇らせ `baseY+h` で wrap。
     * 横位置は `sin` で揺らぎ、上昇につれて粒を小さく＝薄くして消え際を表現する。色は JSON 指定（火＝橙）。
     * `drift`=上昇速度。円描画（ソフトウェア GL でも正常合成）。乱数なし・決定的。
     */
    private void drawLayerEmbers(float baseY, float h, int count, float w, float drift) {
        float span = h <= 0f ? GameConstants.WORLD_HEIGHT : h; // 上昇帯の高さ
        float baseSpeed = drift <= 0f ? 0.7f : drift; // 上昇基準速度（px/frame）
        for (int i = 0; i < count; i++) {
            float speed = baseSpeed * (0.6f + 0.5f * Math.abs((float) Math.sin(i * 1.5f)));
            // baseY から上へ昇り span で wrap（粒ごとに位相をずらす）。
            float rise = (auraTick * speed + i * (span / count)) % span;
            float y = baseY + rise;
            float life = rise / span; // 0（生成・下）→1（消滅・上）
            float sway = 14f * (float) Math.sin(auraTick * 0.05f + i * 1.7f);
            float x0 = (i * (w / count) + sway) % w;
            if (x0 < 0f) {
                x0 += w;
            }
            float r = (2.5f + 2f * Math.abs((float) Math.sin(i * 2.3f))) * (1f - 0.6f * life); // 上昇で縮小
            shapes.circle(x0, y, Math.max(0.8f, r));
        }
    }

    /**
     * 舞台額縁（proscenium）。Task 158。画面の左右端に縦柱を立て、上端を梁で渡してアリーナを縁取る前景。
     * 中央（試合領域）は空けるので、{@code front=true} で手前に描いてもキャラを隠さず奥行き（被写界深度）だけを足す。
     * 柱幅は画面幅比で決定的。各柱は外側が濃く内側がやや明るい 2 段で立体感を出す。
     */
    private void drawLayerFrame(float baseY, float h, float w) {
        float barW = w * 0.072f;     // 各柱の幅（≒92px）。中央の試合領域は十分空く。
        float inner = barW * 0.34f;  // 内側のハイライト帯
        // 左柱。
        shapes.rect(0f, baseY, barW, h);
        // 右柱。
        shapes.rect(w - barW, baseY, barW, h);
        // 上部の梁（左右の柱をつなぐ）。
        shapes.rect(0f, baseY + h * 0.9f, w, h * 0.1f);
        // 内側のハイライト帯（やや明るくして円柱の丸みを示唆）。元色に白を少し混ぜる。
        layerHighlight.set(layerColor).lerp(1f, 1f, 1f, layerColor.a, 0.22f);
        shapes.setColor(layerHighlight);
        shapes.rect(barW - inner, baseY, inner * 0.5f, h * 0.9f);
        shapes.rect(w - barW + inner * 0.5f, baseY, inner * 0.5f, h * 0.9f);
        shapes.setColor(layerColor); // 後続レイヤーのため元色へ戻す
    }

    /** 神殿の柱列（一定間隔の縦矩形）＋上部の梁。Task 151。 */
    private void drawLayerPillars(float baseY, float h, int count, float spacing, float phase) {
        float cw = spacing * 0.38f;
        for (int i = -1; i <= count; i++) {
            float x = i * spacing + phase + (spacing - cw) / 2f;
            shapes.rect(x, baseY, cw, h);
        }
        // 上部の梁（エンタブラチュア）：柱の上端を水平に渡す。
        shapes.rect(0f, baseY + h * 0.86f, GameConstants.WORLD_WIDTH, h * 0.14f);
    }

    /**
     * 空の光の帯（sky sweep）を描く（純描画演出。Task 147）。背景をゆっくり横切る淡い光の縦帯で空気感を出す。
     * 帯の中心 X は描画フレームカウンタ（{@link #auraTick}）で横移動し画面幅で wrap（乱数なし＝決定的）。
     * 帯は中心が最も明るく左右でフェードする 3 枚の縦 rect 近似。スプライト（パス 2）の前＝背景レイヤ。
     */
    private void drawSkySweep() {
        float cycle = GameConstants.WORLD_WIDTH + SKY_SWEEP_WIDTH;
        float centerX = (auraTick * SKY_SWEEP_SPEED) % cycle - SKY_SWEEP_WIDTH / 2f;
        // 中心→端へ 3 段でフェード（中央が最も明るい）。各段は横グラデーションの近似。
        for (int s = 0; s < 3; s++) {
            float t = 1f - s / 3f;                 // 1.0, 0.66, 0.33（中央ほど濃い）
            float w = SKY_SWEEP_WIDTH * (0.34f + s * 0.33f); // 内側ほど細い
            sweepColor.set(SKY_SWEEP_COLOR.r, SKY_SWEEP_COLOR.g, SKY_SWEEP_COLOR.b, SKY_SWEEP_ALPHA * t / 3f);
            shapes.setColor(sweepColor);
            shapes.rect(centerX - w / 2f, GameConstants.GROUND_Y, w, GameConstants.WORLD_HEIGHT - GameConstants.GROUND_Y);
        }
    }

    /**
     * 低 HP 警告ビネット（low-HP vignette）を描く（純描画演出。Task 145）。どちらかのファイターの残量割合が
     * {@link #LOW_HP_RATIO} 以下のとき、画面の四辺を赤くフェードさせ（中心へ向け透明）脈動させて危機感を出す。
     * 不透明度は最も低い残量に応じて強まり、{@link #auraTick} の {@code sin} で脈動する（乱数なし＝決定的）。
     * 表示のみで HP / 当たり判定には干渉しない。最前面寄りのオーバーレイ（パス 3 末尾）で描く。
     */
    private void drawLowHpVignette(Fighter p1, Fighter p2) {
        float lowest = Math.min(p1.getHpRatio(), p2.getHpRatio());
        if (lowest > LOW_HP_RATIO) {
            return; // 双方とも十分な残量＝警告なし（従来どおり何も描かない）。
        }
        // 残量が低いほど・脈動の山ほど濃く。danger = 0（閾値）→1（瀕死）。
        float danger = 1f - lowest / LOW_HP_RATIO;
        float pulse = 0.6f + 0.4f * (float) Math.sin(auraTick * 0.25f);
        float a = LOW_HP_VIGNETTE_ALPHA * Math.max(0f, Math.min(1f, danger)) * pulse;
        float w = GameConstants.WORLD_WIDTH;
        float h = GameConstants.WORLD_HEIGHT;
        float band = LOW_HP_VIGNETTE_BAND;
        vignetteColor.set(LOW_HP_VIGNETTE_COLOR.r, LOW_HP_VIGNETTE_COLOR.g, LOW_HP_VIGNETTE_COLOR.b, a);
        Color edge = vignetteColor;
        Color clear = Color.CLEAR;
        // rect(x,y,w,h, c_bl,c_br,c_tr,c_tl)：辺で edge（赤）→内側で clear（透明）にグラデーション。
        shapes.rect(0f, 0f, band, h, edge, clear, clear, edge);                 // 左
        shapes.rect(w - band, 0f, band, h, clear, edge, edge, clear);           // 右
        shapes.rect(0f, 0f, w, band, edge, edge, clear, clear);                 // 下
        shapes.rect(0f, h - band, w, band, clear, clear, edge, edge);           // 上
    }

    /**
     * 決着 / ラウンド間の演出オーバーレイ（純描画。Task 148/149/150）。{@link ShapeRenderer.ShapeType#Filled}
     * のパス内（テキストバナー = パス 5 より後ろ）で描く。決着中は全画面を暗転（Task 150）して結果バナーを
     * 際立たせ（勝者グロー・Task 149）、その周囲に金色の光の粒が舞い上がる祝祭演出（victory sparkles・Task 150）を
     * 出す。さらに KO 決着直後は数フレーム画面の縁を白くフラッシュ（Task 148）して余韻を作る。乱数なし・表示専用。
     *
     * <p>KO フラッシュは全画面ソリッド rect ではなく**縁グラデーション rect**（{@link #drawEdgeVignette}）で描く：
     * 本環境のソフトウェア GL では全画面ソリッド半透明 rect の合成が不安定だが、縁グラデーション（edge→clear）は
     * 低 HP ビネット（Task 145）で動作実績があるため。**特に半透明の「黒」は暗転にならない**ので、暗転系は避け
     * 非黒（白フラッシュ・金グロー・金の粒）で構成している。
     */
    private void drawRoundEndOverlays(RoundManager round, Fighter p1, Fighter p2) {
        if (round.isFinished() || round.isBetweenRounds()) {
            // 勝者グロー（Task 149）＋勝利の光の粒（Task 150）：ラウンド勝者を金色で祝う（引き分けは無し）。
            RoundManager.Winner winner = round.getRoundWinner();
            Fighter champ = winner == RoundManager.Winner.P1 ? p1
                    : winner == RoundManager.Winner.P2 ? p2 : null;
            if (champ != null) {
                drawWinnerGlow(champ);
                drawVictorySparkles(champ);
            }
        }
        // KO 縁フラッシュ（Task 148）：アーム中は縁を白く、残りフレーム比でフェードしながら描く。
        if (koFlashFrames > 0) {
            koFlashColor.set(KO_FLASH_COLOR.r, KO_FLASH_COLOR.g, KO_FLASH_COLOR.b,
                    KO_FLASH_COLOR.a * koFlashFrames / (float) KO_FLASH_FRAMES);
            drawEdgeVignette(koFlashColor, KO_FLASH_BAND);
            koFlashFrames--;
        }
    }

    /**
     * 勝者の周囲に金色の光の粒が舞い上がる祝祭演出（victory sparkles・Task 150）。粒の位置は粒番号と描画フレーム
     * カウンタ（{@link #auraTick}）からの固定計算＝乱数なし＝決定的。勝者中心を基準に横へ散らし、足元から上昇させ
     * 高さ範囲で wrap する。背景の浮遊パーティクル（Task 139）と同型だが、勝者周辺・金色・上向きの祝祭表現。
     */
    private void drawVictorySparkles(Fighter champ) {
        float baseX = champ.getX();
        float baseY = GameConstants.GROUND_Y;
        shapes.setColor(VICTORY_SPARKLE_COLOR);
        for (int i = 0; i < VICTORY_SPARKLE_COUNT; i++) {
            float x = baseX + VICTORY_SPARKLE_SPREAD * (float) Math.sin(i * 2.4f + auraTick * 0.02f);
            float rise = (i * 53f + auraTick * VICTORY_SPARKLE_RISE) % VICTORY_SPARKLE_HEIGHT;
            float y = baseY + rise;
            float r = VICTORY_SPARKLE_RADIUS * (0.6f + 0.4f * (float) Math.sin(i * 1.7f + auraTick * 0.05f));
            shapes.circle(x, y, r);
        }
    }

    /**
     * 画面の四辺に「縁＝指定色 / 内側＝透明」のグラデーション帯を描く汎用ヘルパー（Task 148/150・低 HP ビネットと同形式）。
     * 全画面ソリッド rect を避けつつ縁演出（暗転フレーム / フラッシュ）を出すために使う。
     */
    private void drawEdgeVignette(Color edge, float band) {
        float w = GameConstants.WORLD_WIDTH;
        float h = GameConstants.WORLD_HEIGHT;
        Color clear = Color.CLEAR;
        shapes.rect(0f, 0f, band, h, edge, clear, clear, edge);                 // 左
        shapes.rect(w - band, 0f, band, h, clear, edge, edge, clear);           // 右
        shapes.rect(0f, 0f, w, band, edge, edge, clear, clear);                 // 下
        shapes.rect(0f, h - band, w, band, clear, clear, edge, edge);           // 上
    }

    /** 勝者の足元に金色のパルス光輪を描く（Task 149）。メーター満タンオーラと同型だがより大きく金色で目立たせる。 */
    private void drawWinnerGlow(Fighter f) {
        Character d = f.getDef();
        float pulse = 1f + 0.18f * (float) Math.sin(auraTick * 0.18f);
        float w = d.getWidth() * WINNER_GLOW_WIDTH_SCALE * pulse;
        float gh = WINNER_GLOW_HEIGHT * pulse;
        winnerGlowColor.set(WINNER_GLOW_COLOR);
        winnerGlowColor.a = WINNER_GLOW_COLOR.a * pulse;
        shapes.setColor(winnerGlowColor);
        shapes.ellipse(f.getX() - w / 2f, GameConstants.GROUND_Y - gh / 2f, w, gh);
    }

    /**
     * ファイターのオーバーレイ要素をパス 3（{@link ShapeRenderer}）で描く（Task 34 で {@code drawFighter} から分離）。
     *
     * <p>スプライト未指定 / 欠落のキャラは従来のプレースホルダ矩形 + 向きマーカー（被弾フラッシュ込み）を
     * ここで描く（後方互換）。スプライト描画済みのキャラは本体矩形を省き、ガードオーバーレイ・攻撃 strike・
     * フレームピップのみを重ねる（これらは矩形 / スプライトの双方に共通の可視化）。
     */
    private void drawFighterOverlay(Fighter f, FighterAnimator anim, Color fallback, boolean paletteSwap) {
        Character d = f.getDef();
        float left = f.getX() - d.getWidth() / 2f;
        // 待機 / 歩行の進行を縦ボブで可視化（空中は物理で位置が変わるためボブ 0）。
        float bottom = f.getY() + anim.bobOffset();
        // しゃがみ中は高さを縮めて低姿勢を可視化（Task 25）。
        float drawHeight = f.isCrouching() ? d.getHeight() / 3f : d.getHeight();
        if (!sprites.isReady(d)) {
            // スプライト未指定 / 欠落：従来のプレースホルダ矩形（キャラ色 / 被弾フラッシュ）+ 向きマーカー。
            Color color = characterColor(d, fallback);
            if (paletteSwap) {
                // ミラーマッチ P2：矩形版でも色を乗算してスプライト版と同じくパレットをずらす（Task 62）。
                color = tintColor.set(color).mul(MIRROR_P2_TINT);
            }
            shapes.setColor(f.isInHitstun() ? hitstunFlash(color) : color);
            shapes.rect(left, bottom, d.getWidth(), drawHeight);
            float markerY = bottom + drawHeight - MARKER_SIZE - 12f;
            float markerX = f.isFacingRight()
                    ? left + d.getWidth() - MARKER_SIZE - 8f
                    : left + 8f;
            shapes.setColor(FACING_COLOR);
            shapes.rect(markerX, markerY, MARKER_SIZE, MARKER_SIZE);
            // クリーンヒットの白フラッシュ（Task 136）：矩形フォールバックは本体矩形 = キャラなので白を重ねて発光させる
            // （スプライトキャラは drawFighterSprite で加算合成によりシルエットを発光させる）。残りフレーム比で減衰。
            int flash = f.getImpactFlashFrames();
            if (flash > 0) {
                impactFlashColor.set(IMPACT_FLASH_COLOR);
                impactFlashColor.a = IMPACT_FLASH_COLOR.a * (flash / (float) GameConstants.IMPACT_FLASH_FRAMES);
                shapes.setColor(impactFlashColor);
                shapes.rect(left, bottom, d.getWidth(), drawHeight);
            }
        }
        // ガード中：半透明ブルーのオーバーレイで盾状態を可視化する（矩形 / スプライト共通。Task 27）。
        if (f.isGuarding()) {
            shapes.setColor(GUARD_COLOR);
            shapes.rect(left, bottom, d.getWidth(), drawHeight);
        }
        // 攻撃中は前方の strike 矩形を区間色（startup=黄 / active=赤 / recovery=灰）で描く。
        if (f.isAttacking()) {
            drawAttackStrike(f);
        }
        // フレームピップ：足元下に総フレーム数だけ並べ、現在フレームを点灯（アニメ進行の証跡）。
        drawFramePips(f, anim);
    }

    /**
     * 攻撃の strike 矩形（技の hitbox 位置）を区間色で描く（Task 11 の可視化）。
     *
     * <p>hitbox は技定義の「前方の前面・足元」基準の相対座標で、向きに応じて左右反転する。
     * 実際の当たり判定（hurtbox との重なり）は Task 12、デバッグ枠表示は Task 18 で扱う。
     */
    private void drawAttackStrike(Fighter f) {
        Move m = f.getCurrentMove();
        // 飛び道具技は body 付随の strike を描かない（弾そのもので可視化する。Task 20）。
        if (m == null || m.isProjectile()) {
            return;
        }
        Character d = f.getDef();
        float frontX = f.isFacingRight()
                ? f.getX() + d.getWidth() / 2f
                : f.getX() - d.getWidth() / 2f;
        float boxX = f.isFacingRight()
                ? frontX + m.getHitboxOffsetX()
                : frontX - m.getHitboxOffsetX() - m.getHitboxWidth();
        // 下段（しゃがみ）攻撃は脚部の低位に描く（CollisionSystem の判定位置と一致させる。Task 31）。
        float offsetY = f.isCrouchAttacking() ? GameConstants.LOW_ATTACK_HITBOX_OFFSET_Y : m.getHitboxOffsetY();
        float boxY = f.getY() + offsetY;
        // 投げ（grab box）は専用の紫、EX 打撃必殺技（Task 54）は金色グロー、それ以外は区間色で strike を描く。
        Color strikeColor = f.isThrowing() ? THROW_COLOR
                : f.isExAttack() ? EX_PROJECTILE_GLOW
                : attackPhaseColor(f.getAttackPhase());
        shapes.setColor(strikeColor);
        shapes.rect(boxX, boxY, m.getHitboxWidth(), m.getHitboxHeight());
    }

    /** キャラ定義の色（長さ 3 の RGB）があればそれを、無ければ既定色を返す（Task 22）。 */
    private static Color characterColor(Character d, Color fallback) {
        float[] c = d.getColor();
        if (c != null && c.length >= 3) {
            return new Color(c[0], c[1], c[2], 1f);
        }
        return fallback;
    }

    /** のけぞり用のフラッシュ色（元色を白へ寄せる）。 */
    private static Color hitstunFlash(Color base) {
        return new Color(
                base.r + (1f - base.r) * 0.6f,
                base.g + (1f - base.g) * 0.6f,
                base.b + (1f - base.b) * 0.6f,
                1f);
    }

    /**
     * 飛び道具を外側グロー + 内側コアの二重円で描く（Task 20）。EX 弾（Task 44）は金色のグローで強調。
     * 描画前に直近の通過位置へ薄く小さい尾（motion trail・Task 134）を引いて速度感・残像感を出す。
     */
    private void drawProjectiles(List<Projectile> projectiles) {
        for (Projectile p : projectiles) {
            float cx = p.getX();
            float cy = p.getY() + p.getHeight() / 2f;
            float r = Math.min(p.getWidth(), p.getHeight()) / 2f;
            Color glow = p.isEx() ? EX_PROJECTILE_GLOW : PROJECTILE_GLOW;
            // 軌跡（尾）：過去位置を「最古→最新」で薄→濃・小→大の円として本体の前に描く（Task 134）。
            int trail = p.getTrailSize();
            for (int i = 0; i < trail; i++) {
                float t = trail > 1 ? i / (float) (trail - 1) : 1f; // 0=最古 → 1=最新
                projectileTrailColor.set(glow.r, glow.g, glow.b, PROJECTILE_TRAIL_ALPHA * t);
                shapes.setColor(projectileTrailColor);
                shapes.circle(p.getTrailX(i), cy, r * (PROJECTILE_TRAIL_MIN_SCALE
                        + (PROJECTILE_TRAIL_MAX_SCALE - PROJECTILE_TRAIL_MIN_SCALE) * t));
            }
            shapes.setColor(glow);
            shapes.circle(cx, cy, r);
            shapes.setColor(PROJECTILE_CORE);
            shapes.circle(cx, cy, r * 0.55f);
        }
    }

    /**
     * ヒットスパークを描く（Task 38）。各スパークは命中位置から放射状のスポーク（三角形）を伸ばしつつ、
     * 経過に比例してスポークが外へ伸び・コアが縮み・全体がフェードする。通常ヒットは暖色、ガードは寒色。
     * {@link ShapeRenderer.ShapeType#Filled} のオーバーレイパス内で呼ぶ（ブレンドは有効化済み）。
     */
    private void drawHitSparks(List<HitSpark> sparks) {
        if (sparks.isEmpty()) {
            return;
        }
        for (HitSpark s : sparks) {
            float progress = s.getLifespan() > 0 ? (float) s.getAge() / s.getLifespan() : 1f;
            float alpha = Math.max(0f, 1f - progress); // 線形フェードアウト
            Color base = s.getKind() == HitSpark.Kind.GUARD ? SPARK_GUARD_COLOR : SPARK_HIT_COLOR;
            sparkColor.set(base.r, base.g, base.b, alpha);
            shapes.setColor(sparkColor);
            float cx = s.getOriginX();
            float cy = s.getOriginY();
            float inner = SPARK_CORE_RADIUS * 0.5f;          // スポーク基部の半径
            float outer = inner + progress * SPARK_REACH;    // スポーク先端の半径（外へ伸びる）
            float half = SPARK_SPOKE_HALF_WIDTH * (1f - progress * 0.5f); // 先細りの基部幅
            // 放射スポーク（先端の鋭い三角形を SPARK_SPOKES 本）。
            for (int i = 0; i < SPARK_SPOKES; i++) {
                double a = (Math.PI * 2.0 * i) / SPARK_SPOKES;
                float dx = (float) Math.cos(a);
                float dy = (float) Math.sin(a);
                float px = -dy; // 垂直方向（基部の幅付け用）
                float py = dx;
                float tipX = cx + dx * outer;
                float tipY = cy + dy * outer;
                float b1x = cx + dx * inner + px * half;
                float b1y = cy + dy * inner + py * half;
                float b2x = cx + dx * inner - px * half;
                float b2y = cy + dy * inner - py * half;
                shapes.triangle(tipX, tipY, b1x, b1y, b2x, b2y);
            }
            // 中心コア（時間とともに縮む明るい円）。
            shapes.circle(cx, cy, SPARK_CORE_RADIUS * (1f - progress));
        }
    }

    /**
     * 着地の砂煙（足元の土埃）を描く（Task 131）。各砂煙について、経過進捗から「横への広がり・上昇・
     * フェード・膨らみ」を導出し、複数の小さな丸を左右対称に配置する。粒の位置は粒番号から決まる固定
     * オフセット＝乱数なし＝決定的（入力リプレイと両立）。{@link ShapeRenderer.ShapeType#Filled} の
     * オーバーレイパス（パス 3）内で呼ぶ。HP / 位置 / 当たり判定には一切干渉しない純粋な演出。
     */
    private void drawLandingDust(List<LandingDust> dusts) {
        if (dusts.isEmpty()) {
            return;
        }
        int half = (DUST_PUFFS + 1) / 2; // 片側の段数（広がりの正規化分母）
        for (LandingDust d : dusts) {
            float progress = d.getLifespan() > 0 ? (float) d.getAge() / d.getLifespan() : 1f;
            float alpha = Math.max(0f, 1f - progress); // 線形フェードアウト
            dustColor.set(DUST_COLOR.r, DUST_COLOR.g, DUST_COLOR.b, DUST_COLOR.a * alpha);
            shapes.setColor(dustColor);
            float baseX = d.getOriginX();
            float baseY = d.getOriginY();
            for (int i = 0; i < DUST_PUFFS; i++) {
                float side = (i % 2 == 0) ? 1f : -1f;          // 左右交互
                float rank = (i / 2) + 1;                       // 1,1,2,2,3,3 … 外側ほど大
                float dirScale = rank / (float) half;          // 0..1 に正規化
                float x = baseX + side * progress * DUST_SPREAD * dirScale;
                float y = baseY + progress * DUST_RISE * (1f - dirScale * 0.4f) + 2f;
                float r = DUST_PUFF_RADIUS * (0.6f + progress * 0.7f); // わずかに膨らむ
                shapes.circle(x, y, r);
            }
        }
    }

    /** active hitbox が相手 hurtbox に重なるフレームに、接触位置へ白い火花マーカーを描く（Task 12）。 */
    private void drawContactMarker(Fighter attacker, Fighter defender) {
        if (!CollisionSystem.isHitting(attacker, defender)) {
            return;
        }
        Hitbox hb = CollisionSystem.activeHitbox(attacker);
        float cx = hb.getX() + hb.getWidth() / 2f;
        float cy = hb.getY() + hb.getHeight() / 2f;
        float s = 28f;
        shapes.setColor(CONTACT_COLOR);
        shapes.rect(cx - s / 2f, cy - s / 2f, s, s);
    }

    /** 攻撃区間に応じた strike 色（startup=黄 / active=赤 / recovery=灰）。 */
    private static Color attackPhaseColor(AttackPhase phase) {
        switch (phase) {
            case STARTUP:
                return ATK_STARTUP_COLOR;
            case ACTIVE:
                return ATK_ACTIVE_COLOR;
            case RECOVERY:
                return ATK_RECOVERY_COLOR;
            default:
                return ATK_RECOVERY_COLOR;
        }
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

    /**
     * ダメージ数値ポップアップを描く。各ポップアップは命中位置から経過フレームに比例して上昇し、終盤
     * （{@link #POPUP_FADE_START} 以降）でフェードアウトする。通常ヒットは黄、ガード chip は青で色分けする。
     * テキストパス（{@link SpriteBatch}）内で呼び、フォントの倍率・色は最後に既定（白・等倍）へ戻す。
     */
    private void drawDamagePopups(List<DamagePopup> popups) {
        if (popups.isEmpty()) {
            return;
        }
        for (DamagePopup p : popups) {
            float progress = p.getLifespan() > 0 ? (float) p.getAge() / p.getLifespan() : 1f;
            // フェード：前半は不透明、POPUP_FADE_START 以降で 1→0 へ線形に消す。
            float alpha = progress < POPUP_FADE_START
                    ? 1f
                    : Math.max(0f, 1f - (progress - POPUP_FADE_START) / (1f - POPUP_FADE_START));
            Color base = p.getKind() == DamagePopup.Kind.CHIP ? POPUP_CHIP_COLOR : POPUP_HIT_COLOR;
            popupColor.set(base.r, base.g, base.b, alpha);
            font.setColor(popupColor);
            // ダメージ量に応じて文字を拡大（Task 142）：大ダメージほど数字が大きく出て重みを表す。
            float dmgScale = POPUP_SCALE * (POPUP_MIN_SCALE_FACTOR
                    + (1f - POPUP_MIN_SCALE_FACTOR) * Math.min(p.getAmount(), POPUP_SCALE_DAMAGE_REF) / (float) POPUP_SCALE_DAMAGE_REF);
            font.getData().setScale(dmgScale);
            float y = p.getOriginY() + POPUP_BASE_OFFSET_Y + p.getAge() * POPUP_RISE_PER_FRAME;
            drawCentered(String.valueOf(p.getAmount()), p.getOriginX(), y);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
    }

    /** ファイターの名前と現在の状態（攻撃中は区間、それ以外はアニメ状態 / フレーム）を矩形の上に表示する。 */
    private void drawNameLabel(Fighter f, FighterAnimator anim) {
        float centerX = f.getX();
        float displayHeight = f.isCrouching() ? f.getDef().getHeight() / 3f : f.getDef().getHeight();
        float top = f.getY() + displayHeight;
        drawCentered(f.getDef().getName(), centerX, top + 30f);
        String stateLabel;
        if (f.isKnockedDown()) {
            // ダウン（Task 60）。HITSTUN ポーズを流用しつつ knockdown ラベルで識別する（ダウン中は被弾無敵）。
            // 受け身（Task 66）成立中は knockdown(ukemi) として識別する（クイック起き上がりの証跡）。
            stateLabel = f.isUkemiRecovering() ? "knockdown(ukemi)"
                    : f.isDelayingWakeup() ? "knockdown(delay)"
                    : f.isHardKnockedDown() ? "knockdown(hard)" : "knockdown";
        } else if (f.isThrowTeched()) {
            // 投げ抜けの硬直は hitstun フレームを流用するため、ラベルは tech を優先表示する（Task 36）。
            stateLabel = "tech";
        } else if (f.isGuardBroken()) {
            // ガードクラッシュも hitstun を流用するため、ラベルは guard_break を hitstun より先に表示する（Task 43）。
            stateLabel = STATE_LABEL_GUARD_BREAK;
        } else if (f.isDizzy()) {
            // めまい（Task 79）。HITSTUN ポーズを流用しつつ dizzy ラベルで識別（被弾無敵ではない無防備硬直）。
            stateLabel = "dizzy";
        } else if (f.isWallBounced()) {
            // 壁バウンド成立（Task 101）。HITSTUN を流用しつつ wall_bounce ラベルで跳ね返りを識別する。
            stateLabel = "wall_bounce";
        } else if (f.isGroundBounced()) {
            // 床バウンド成立（Task 102）。HITSTUN を流用しつつ ground_bounce ラベルで跳ね返りを識別する。
            stateLabel = "ground_bounce";
        } else if (f.isInHitstun()) {
            stateLabel = "hitstun " + f.getHitstunFrames();
        } else if (f.isAirTeching()) {
            // 空中受け身（air recovery・Task 126）。滞空のため JUMP ポーズを流用しつつ air_tech ラベルで識別する
            // （空中やられを抜けた直後の行動不能リカバリ＝被弾無敵ではない committal な脱出）。
            stateLabel = "air_tech";
        } else if (f.isParrying()) {
            // パリィ成立（Task 105）。行動はロックしないが、成立直後の数フレームを parry ラベルで識別する（反撃確定の証跡）。
            stateLabel = "parry";
        } else if (f.isAttacking()) {
            String prefix = f.isThrowing() ? "throw"
                    : f.isSpecialActive() ? "special"
                    : f.isDashAttacking() ? "dash_attack"
                    : (f.isCrouchAttacking() ? "crouch_attack" : "attack");
            stateLabel = prefix + ":" + f.getAttackPhase().name().toLowerCase();
        } else if (f.isDashing()) {
            // ダッシュ（二度押しステップ・Task 49）／ラン（Task 123）。歩行アニメを流用しつつラベルで識別する。
            stateLabel = f.isRunning() ? "run" : "dash";
        } else if (f.isAirGuarding()) {
            // 空中ガード（Task 59）。滞空のため JUMP ポーズを流用しつつ、青オーバーレイとこのラベルで識別する。
            stateLabel = "air_guard";
        } else {
            stateLabel = anim.getState().label() + " f" + anim.getFrameIndex();
        }
        // 無敵フレーム中（リバーサル / 対空・Task 53）は状態ラベルに [INV] を付して可視化する
        // （フレームデータ依存の無敵をスクショで確認できるようにする）。
        if (f.isInvincible()) {
            stateLabel = stateLabel + STATE_LABEL_INVINCIBLE_SUFFIX;
        }
        // EX 必殺技中（メーター消費の強化版・Task 54）は [EX] を付す（金色 strike と対）。
        if (f.isExAttack()) {
            stateLabel = stateLabel + STATE_LABEL_EX_SUFFIX;
        }
        // スーパー必殺技中（メーター満タン消費・Task 108）は [SUPER] を付す（発動時にスーパーフラッシュ凍結）。
        if (f.isAttacking() && f.getCurrentMove() != null && f.getCurrentMove().isSuper()) {
            stateLabel = stateLabel + STATE_LABEL_SUPER_SUFFIX;
        }
        // カウンターヒット被弾中（Task 71）は (CH) を付す（差し返された証跡）。
        if (f.isCounterHit()) {
            stateLabel = stateLabel + STATE_LABEL_COUNTER_SUFFIX;
        }
        // スーパーアーマー有効中（Task 80）は [ARMOR] を付す（のけぞらない startup の証跡）。
        if (f.isArmorActive()) {
            stateLabel = stateLabel + STATE_LABEL_ARMOR_SUFFIX;
        }
        // ジャストガード成立直後（Task 81）は [JUST] を付す（chip なし完全防御の証跡）。
        if (f.isJustGuarding()) {
            stateLabel = stateLabel + STATE_LABEL_JUST_SUFFIX;
        }
        drawCentered(stateLabel, centerX, top + 12f);
    }

    /**
     * 連続ヒット中（{@code comboCount >= 2}）のファイターの頭上に "N HITS!" を表示する（Task 39）。
     * テキストパス（{@link SpriteBatch}）内で呼び、フォントの色・倍率は最後に既定（白・等倍）へ戻す。
     */
    private void drawComboCounter(Fighter f) {
        int combo = f.getComboCount();
        if (combo < 2) {
            return;
        }
        float displayHeight = f.isCrouching() ? f.getDef().getHeight() / 3f : f.getDef().getHeight();
        float y = f.getY() + displayHeight + 58f; // 名前ラベル（+30）のさらに上
        // コンボ継続中は "N HITS!" を小刻みに拡大パルスさせて勢いを出す（Task 143・auraTick の sin・乱数なし）。
        float pulse = 1f + COMBO_PULSE * (float) Math.sin(auraTick * COMBO_PULSE_SPEED);
        font.getData().setScale(COMBO_SCALE * pulse);
        font.setColor(COMBO_COLOR);
        drawCentered(combo + " HITS!", f.getX(), y);
        // コンボ累計ダメージ（Task 121）：ヒット数の下に補正後の実ダメージ合計を表示する。
        font.getData().setScale(COMBO_SCALE * 0.7f);
        drawCentered(f.getComboDamage() + " DMG", f.getX(), y - 24f);
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
    }

    /**
     * ガードクラッシュ中（{@link Fighter#isGuardBroken()}）のファイターの頭上に "GUARD BREAK!" を表示する（Task 43）。
     * テキストパス内で呼び、フォントの色・倍率は最後に既定（白・等倍）へ戻す（共有状態リーク防止）。
     */
    private void drawGuardBreakLabel(Fighter f) {
        if (!f.isGuardBroken()) {
            return;
        }
        float displayHeight = f.isCrouching() ? f.getDef().getHeight() / 3f : f.getDef().getHeight();
        float y = f.getY() + displayHeight + 58f;
        font.getData().setScale(GUARD_BREAK_SCALE);
        font.setColor(GUARD_BREAK_COLOR);
        drawCenteredClamped(TEXT_GUARD_BREAK, f.getX(), y, 12f); // 画面端でも見切れないようクランプ
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
    }

    /**
     * P1 の直近入力ログ（テンキー方向＋ボタン）を画面左端に縦に並べて表示する（入力表示 HUD・Task 96）。
     * 最新を下にして上から古い順に並べる（FG 定番の表示向き）。空なら何も描かない。テキストパス内で呼び、
     * フォント倍率は描画後に既定へ戻す（共有状態リーク防止）。
     */
    private void drawInputDisplay(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        font.getData().setScale(INPUT_DISPLAY_SCALE);
        font.setColor(INPUT_DISPLAY_COLOR);
        float x = 16f;
        float lineH = 20f;
        int n = inputs.size();
        // 最新を最下行に：縦位置の起点を中段やや上に置き、下へ向かって新しい入力を描く。
        float topY = 360f + (n - 1) * lineH;
        for (int i = 0; i < n; i++) {
            font.draw(batch, inputs.get(i), x, topY - i * lineH);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
    }

    /**
     * タイトル画面を描く（Task 116）。モード選択（0=対戦 / 1=トレーニング）。選択中の項目を黄色で強調する。
     * 独立した clear + テキストパス（バトル描画とは別フレーム）。
     */
    public void renderTitle(int selection) {
        ScreenUtils.clear(0.05f, 0.05f, 0.10f, 1f);
        centerCamera(); // hit shake のオフセットがメニューへ漏れないよう中心へ戻す（Task 132）
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(2.4f);
        font.setColor(TITLE_ACCENT_COLOR);
        drawCentered("PHANTOM NEXUS", GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 180f);
        font.getData().setScale(1.5f);
        font.setColor(selection == 0 ? Color.YELLOW : Color.WHITE);
        drawCentered((selection == 0 ? "> " : "  ") + "VERSUS" + (selection == 0 ? " <" : ""),
                GameConstants.WORLD_WIDTH / 2f, 380f);
        font.setColor(selection == 1 ? Color.YELLOW : Color.WHITE);
        drawCentered((selection == 1 ? "> " : "  ") + "TRAINING" + (selection == 1 ? " <" : ""),
                GameConstants.WORLD_WIDTH / 2f, 312f);
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
        drawCentered("UP / DOWN : select      ENTER : confirm", GameConstants.WORLD_WIDTH / 2f, 200f);
        drawCentered("TRAINING = Player 2 does nothing (infinite HP practice)",
                GameConstants.WORLD_WIDTH / 2f, 168f);
        batch.end();
    }

    /**
     * キャラクター選択画面を描く（Task 117）。ロスターをグリッド表示し、カーソル（黄）・P1 確定（シアン）・P2 確定（橙）を
     * 色で区別する。上部に選択中プレイヤーと確定済みの選択を表示する。独立した clear + テキストパス。
     */
    public void renderCharacterSelect(String[] names, int cursor, int p1, int p2, boolean p1Locked, int cols) {
        ScreenUtils.clear(0.05f, 0.05f, 0.10f, 1f);
        centerCamera(); // hit shake のオフセットがメニューへ漏れないよう中心へ戻す（Task 132）
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.6f);
        font.setColor(TITLE_ACCENT_COLOR);
        drawCentered("CHARACTER SELECT", GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 70f);
        font.getData().setScale(1.1f);
        font.setColor(p1Locked ? CHARSEL_P2_COLOR : CHARSEL_P1_COLOR);
        drawCentered(p1Locked ? "Player 2 : choose your fighter" : "Player 1 : choose your fighter",
                GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 130f);
        font.getData().setScale(0.95f);
        float gridLeft = 150f;
        float gridTop = 460f;
        float cellW = 165f;
        float rowH = 64f;
        for (int i = 0; i < names.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float cx = gridLeft + col * cellW + cellW / 2f;
            float cy = gridTop - row * rowH;
            Color c = Color.WHITE;
            String label = names[i];
            if (i == p1) {
                c = CHARSEL_P1_COLOR;
                label = "1>" + label;
            } else if (i == p2) {
                c = CHARSEL_P2_COLOR;
                label = "2>" + label;
            }
            if (i == cursor) {
                c = Color.YELLOW;
                label = "[" + names[i] + "]";
            }
            font.setColor(c);
            drawCentered(label, cx, cy);
        }
        font.getData().setScale(1.0f);
        font.setColor(CHARSEL_P1_COLOR);
        drawCentered("P1: " + (p1 >= 0 ? names[p1] : "..."), GameConstants.WORLD_WIDTH / 2f - 180f, 150f);
        font.setColor(CHARSEL_P2_COLOR);
        drawCentered("P2: " + (p2 >= 0 ? names[p2] : "..."), GameConstants.WORLD_WIDTH / 2f + 180f, 150f);
        font.setColor(Color.WHITE);
        drawCentered("ARROWS / WASD : move      ENTER : confirm", GameConstants.WORLD_WIDTH / 2f, 100f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    /**
     * ステージ選択画面を描く（Task 128）。全ステージ名をグリッド表示し、カーソル（黄）を強調する。
     * キャラ選択（Task 117）と同じグリッド作法。確定で選んだステージが対戦の背景になる。独立した clear + テキストパス。
     */
    public void renderStageSelect(String[] names, int cursor, int cols) {
        ScreenUtils.clear(0.05f, 0.05f, 0.10f, 1f);
        centerCamera(); // hit shake のオフセットがメニューへ漏れないよう中心へ戻す（Task 132）
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.6f);
        font.setColor(TITLE_ACCENT_COLOR);
        drawCentered("STAGE SELECT", GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 70f);
        font.getData().setScale(1.1f);
        font.setColor(Color.WHITE);
        drawCentered("choose your stage", GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 130f);
        font.getData().setScale(0.95f);
        float gridLeft = 90f;
        float gridTop = 420f;
        float cellW = 220f;
        float rowH = 80f;
        for (int i = 0; i < names.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float cx = gridLeft + col * cellW + cellW / 2f;
            float cy = gridTop - row * rowH;
            font.setColor(i == cursor ? Color.YELLOW : Color.WHITE);
            String label = i == cursor ? "[" + names[i] + "]" : names[i];
            drawCentered(label, cx, cy);
        }
        font.getData().setScale(1.0f);
        font.setColor(TITLE_ACCENT_COLOR);
        drawCentered("Stage: " + names[cursor], GameConstants.WORLD_WIDTH / 2f, 150f);
        font.setColor(Color.WHITE);
        drawCentered("ARROWS / WASD : move      ENTER : confirm", GameConstants.WORLD_WIDTH / 2f, 100f);
        font.getData().setScale(1.0f);
        batch.end();
    }

    /**
     * コマンド表 HUD（Task 112）：両ファイターの技/コマンド一覧を画面左右に描く（F5 トグル）。
     * データはキャラ定義（{@link Character}）から組み立てる純表示。通常技はボタン（L/M/H）、必殺技はコマンド表記、
     * 投げ・スーパーも列挙する。トレーニング / 観戦時の参照用。
     */
    private void drawMoveList(Fighter p1, Fighter p2) {
        font.getData().setScale(0.85f);
        font.setColor(MOVE_LIST_COLOR);
        drawMoveListColumn(p1.getDef(), 24f);
        drawMoveListColumn(p2.getDef(), GameConstants.WORLD_WIDTH - 320f);
        font.setColor(Color.WHITE);
        font.getData().setScale(1.0f);
    }

    /** コマンド表の 1 キャラ分を左上原点（{@code x}）から下方向へ描く（Task 112）。 */
    private void drawMoveListColumn(Character def, float x) {
        float y = 600f;
        float lineH = 22f;
        font.draw(batch, "[" + def.getName() + "] moves", x, y);
        y -= lineH;
        Move[] normals = def.getNormalMoves();
        if (normals != null) {
            for (Move m : normals) {
                if (m == null) {
                    continue;
                }
                String btn = m.getButton() != null ? m.getButton().name().substring(0, 1) : "?";
                font.draw(batch, btn + " : " + m.getId(), x, y);
                y -= lineH;
            }
        }
        Move[] specials = def.getSpecialMoves();
        if (specials != null) {
            for (Move m : specials) {
                if (m == null) {
                    continue;
                }
                font.draw(batch, commandLabel(m.getCommand()) + " : " + m.getId(), x, y);
                y -= lineH;
            }
        }
        if (def.getThrowMove() != null) {
            font.draw(batch, "throw : " + def.getThrowMove().getId(), x, y);
            y -= lineH;
        }
    }

    /** コマンド名（{@code Command.name()}）をテンキー表記の短いラベルへ変換する（Task 112）。 */
    private static String commandLabel(String command) {
        if (command == null) {
            return "?";
        }
        switch (command.trim().toUpperCase()) {
            case "HADOUKEN":
                return "236+A";
            case "CHARGE_SHOT":
                return "[4]6+A";
            case "DOWN_ATTACK":
                return "2+A";
            case "SUPER":
                return "236236+A";
            default:
                return command;
        }
    }

    /** 指定文字列を中心 X（{@code centerX}）・ベースライン Y（{@code y}）に水平センタリングで描く。 */
    private void drawCentered(String text, float centerX, float y) {
        layout.setText(font, text);
        font.draw(batch, layout, centerX - layout.width / 2f, y);
    }

    /**
     * センタリング描画だが、左端 X が画面内（左右 {@code margin} 余白）に収まるようクランプする。
     * 画面端のファイター頭上に出すフローティングラベル（"GUARD BREAK!" 等）が画面外で見切れるのを防ぐ。
     */
    private void drawCenteredClamped(String text, float centerX, float y, float margin) {
        layout.setText(font, text);
        float left = centerX - layout.width / 2f;
        float maxLeft = GameConstants.WORLD_WIDTH - margin - layout.width;
        left = Math.max(margin, Math.min(left, maxLeft));
        font.draw(batch, layout, left, y);
    }

    /** ウィンドウリサイズ時にビューポートを追従させる。 */
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /** GPU リソースの解放。 */
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        sprites.dispose();
        font.dispose();
    }

    /**
     * ダッシュ残像（Task 133）の軌跡を保持するリングバッファ。1 ファイター分の直近 {@link #AFTERIMAGE_MAX}
     * フレームの実位置スナップショット（中心 X / 足元 Y + ボブ / アニメ状態 / フレーム / 向き / しゃがみ）を
     * 上書き式で溜める。描画専用の純粋な状態で、戦闘ロジックや乱数には一切関与しない。
     */
    private static final class GhostTrail {
        final float[] x = new float[AFTERIMAGE_MAX];
        final float[] y = new float[AFTERIMAGE_MAX];
        final AnimationState[] state = new AnimationState[AFTERIMAGE_MAX];
        final int[] frame = new int[AFTERIMAGE_MAX];
        final boolean[] faceLeft = new boolean[AFTERIMAGE_MAX];
        final boolean[] crouch = new boolean[AFTERIMAGE_MAX];
        int size; // 有効なスナップショット数（0..AFTERIMAGE_MAX）
        int head; // 次に書き込む位置（リングバッファ）

        /** 軌跡を空にする（ダッシュ終了時）。 */
        void clear() {
            size = 0;
            head = 0;
        }

        /** 最新のスナップショットを 1 件追加する（容量超過時は最古を上書き）。 */
        void push(float px, float py, AnimationState st, int fr, boolean fl, boolean cr) {
            x[head] = px;
            y[head] = py;
            state[head] = st;
            frame[head] = fr;
            faceLeft[head] = fl;
            crouch[head] = cr;
            head = (head + 1) % AFTERIMAGE_MAX;
            if (size < AFTERIMAGE_MAX) {
                size++;
            }
        }
    }
}
