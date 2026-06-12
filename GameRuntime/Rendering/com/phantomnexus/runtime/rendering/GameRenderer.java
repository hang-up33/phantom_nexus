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
    private static final Color ATK_STARTUP_COLOR = new Color(0.96f, 0.82f, 0.28f, 0.85f);
    private static final Color ATK_ACTIVE_COLOR = new Color(0.95f, 0.25f, 0.22f, 0.9f);
    private static final Color ATK_RECOVERY_COLOR = new Color(0.55f, 0.57f, 0.64f, 0.8f);
    // 投げ（grab box）の strike 矩形色。通常攻撃（黄→赤→灰）と区別する紫（Task 35）。
    private static final Color THROW_COLOR = new Color(0.82f, 0.38f, 0.95f, 0.9f);
    private static final Color CONTACT_COLOR = new Color(1f, 1f, 1f, 1f);
    private static final Color PROJECTILE_CORE = new Color(1f, 0.95f, 0.7f, 1f);
    private static final Color PROJECTILE_GLOW = new Color(0.45f, 0.85f, 1f, 1f);
    private static final Color GUARD_COLOR = new Color(0.30f, 0.70f, 1f, 0.55f);
    // ヒットスパーク（Task 38）：通常ヒット=暖色（白寄りの黄）/ ガード=寒色（青）。放射スポーク数と寸法。
    private static final Color SPARK_HIT_COLOR = new Color(1f, 0.95f, 0.55f, 1f);
    private static final Color SPARK_GUARD_COLOR = new Color(0.60f, 0.85f, 1f, 1f);
    // コンボカウンター（Task 39）の文字色（鮮やかなオレンジ）と表示倍率。
    private static final Color COMBO_COLOR = new Color(1f, 0.62f, 0.18f, 1f);
    private static final float COMBO_SCALE = 1.7f;
    // ラウンド開始イントロ（Task 42）："ROUND N"=白系 / "FIGHT!"=赤系で開始を強調。
    private static final Color ROUND_INTRO_COLOR = new Color(0.96f, 0.96f, 0.98f, 1f);
    private static final Color FIGHT_FLASH_COLOR = new Color(0.98f, 0.30f, 0.26f, 1f);
    private static final int SPARK_SPOKES = 8;        // 放射スポーク本数
    private static final float SPARK_CORE_RADIUS = 9f; // 中心コア（縮小していく）の初期半径
    private static final float SPARK_REACH = 34f;      // スポーク先端が到達する最大距離
    private static final float SPARK_SPOKE_HALF_WIDTH = 4f; // スポーク基部の半幅
    // ダメージ数値ポップアップ：通常ヒット=暖色（黄）/ ガード chip=寒色（青、GUARD_COLOR と同系）。
    private static final Color POPUP_HIT_COLOR = new Color(1f, 0.92f, 0.40f, 1f);
    private static final Color POPUP_CHIP_COLOR = new Color(0.55f, 0.80f, 1f, 1f);
    private static final float POPUP_RISE_PER_FRAME = 1.3f; // 1 フレームあたりの上昇量（px）
    private static final float POPUP_BASE_OFFSET_Y = 36f;    // 命中位置からの初期持ち上げ（px）
    private static final float POPUP_SCALE = 1.7f;           // 数字フォント倍率
    private static final float POPUP_FADE_START = 0.6f;      // この進捗以降フェード開始（0..1）
    private static final Color WIN_DOT_ON = new Color(1f, 0.85f, 0.20f, 1f);
    private static final Color WIN_DOT_OFF = new Color(0.28f, 0.30f, 0.36f, 1f);
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
    // 現在のステージ色（Task 17）。未設定時はフォールバックを使う。
    private final Color skyTop = new Color(SKY_TOP_FALLBACK);
    private final Color skyBottom = new Color(SKY_BOTTOM_FALLBACK);
    private final Color groundColor = new Color(GROUND_COLOR);
    private String stageName = "";

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
                            RoundManager round, DebugOverlay debug, String controlsHint, String statusLine) {
        ScreenUtils.clear(GameConstants.BG_R, GameConstants.BG_G, GameConstants.BG_B, GameConstants.BG_A);
        camera.update();
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
        // 地面（床）。
        shapes.setColor(groundColor);
        shapes.rect(0f, 0f, GameConstants.WORLD_WIDTH, GameConstants.GROUND_Y);
        shapes.end();

        // ミラーマッチ（同キャラ対戦）なら P2 にパレットスワップを適用して識別する（Task 62）。
        // 別キャラ対戦では false ＝従来どおりの見た目（既存スクショ / レシピに回帰しない）。
        boolean mirror = p1.getDef().getId().equals(p2.getDef().getId());

        // --- パス 2: キャラクターのスプライト（テクスチャ描画。Task 34）---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawFighterSprite(p1, anim1, false);
        drawFighterSprite(p2, anim2, mirror);
        batch.end();

        // --- パス 3: オーバーレイ（矩形フォールバック / ガード / 攻撃 strike / 接触 / 飛び道具 / HP）---
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawFighterOverlay(p1, anim1, P1_COLOR, false);
        drawFighterOverlay(p2, anim2, P2_COLOR, mirror);
        // 飛び道具（必殺技の弾）。
        drawProjectiles(projectiles);
        // ヒットスパーク（命中位置で拡大＋フェードする火花。Task 38）。
        drawHitSparks(sparks);
        // ヒット接触マーカー（active hitbox × 相手 hurtbox が重なるフレームに点灯）。
        drawContactMarker(p1, p2);
        drawContactMarker(p2, p1);
        // HP ゲージ（HUD 上端）。P1 は左から、P2 は右から減る方向に塗る。
        drawHpBar(p1, true);
        drawHpBar(p2, false);
        // ガードゲージ（HP バーの直下の細バー。ガードで減り、尽きるとガードクラッシュ。Task 43）。
        drawGuardGauge(p1, true);
        drawGuardGauge(p2, false);
        // 必殺技ゲージ（画面下端の細バー。貯まると EX 必殺技が撃てる。Task 44）。
        drawSuperMeter(p1, true);
        drawSuperMeter(p2, false);
        // 勝利ラウンド数を示すドット（HP バー内側端の下）。金色=獲得、暗色=未獲得。
        drawWinDots(round);
        shapes.end();

        // --- パス 4: デバッグ当たり判定枠（有効時のみ。Line で重ね描き。投影は上で設定済み）---
        debug.drawBoxes(shapes, p1, p2);

        // --- パス 5: テキスト（タイトル / 名前 + アニメ状態ラベル / HP 数値 / 入力 HUD） ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(1.5f);
        drawCentered(GameConstants.WINDOW_TITLE, GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - 30f);
        // ラウンドタイマー（HUD 中央上、HP バー帯の高さ）。
        drawCentered(String.valueOf(round.getRemainingSeconds()),
                GameConstants.WORLD_WIDTH / 2f, GameConstants.WORLD_HEIGHT - HP_BAR_TOP + 4f);
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

    /** 飛び道具を外側グロー + 内側コアの二重円で描く（Task 20）。EX 弾（Task 44）は金色のグローで強調。 */
    private void drawProjectiles(List<Projectile> projectiles) {
        for (Projectile p : projectiles) {
            float cx = p.getX();
            float cy = p.getY() + p.getHeight() / 2f;
            float r = Math.min(p.getWidth(), p.getHeight()) / 2f;
            shapes.setColor(p.isEx() ? EX_PROJECTILE_GLOW : PROJECTILE_GLOW);
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
        font.getData().setScale(POPUP_SCALE);
        for (DamagePopup p : popups) {
            float progress = p.getLifespan() > 0 ? (float) p.getAge() / p.getLifespan() : 1f;
            // フェード：前半は不透明、POPUP_FADE_START 以降で 1→0 へ線形に消す。
            float alpha = progress < POPUP_FADE_START
                    ? 1f
                    : Math.max(0f, 1f - (progress - POPUP_FADE_START) / (1f - POPUP_FADE_START));
            Color base = p.getKind() == DamagePopup.Kind.CHIP ? POPUP_CHIP_COLOR : POPUP_HIT_COLOR;
            popupColor.set(base.r, base.g, base.b, alpha);
            font.setColor(popupColor);
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
            stateLabel = "knockdown";
        } else if (f.isThrowTeched()) {
            // 投げ抜けの硬直は hitstun フレームを流用するため、ラベルは tech を優先表示する（Task 36）。
            stateLabel = "tech";
        } else if (f.isGuardBroken()) {
            // ガードクラッシュも hitstun を流用するため、ラベルは guard_break を hitstun より先に表示する（Task 43）。
            stateLabel = STATE_LABEL_GUARD_BREAK;
        } else if (f.isInHitstun()) {
            stateLabel = "hitstun " + f.getHitstunFrames();
        } else if (f.isAttacking()) {
            String prefix = f.isThrowing() ? "throw"
                    : f.isSpecialActive() ? "special"
                    : (f.isCrouchAttacking() ? "crouch_attack" : "attack");
            stateLabel = prefix + ":" + f.getAttackPhase().name().toLowerCase();
        } else if (f.isDashing()) {
            // ダッシュ（二度押しステップ・Task 49）。歩行アニメを流用しつつラベルで識別する。
            stateLabel = "dash";
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
        font.getData().setScale(COMBO_SCALE);
        font.setColor(COMBO_COLOR);
        drawCentered(combo + " HITS!", f.getX(), y);
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
}
