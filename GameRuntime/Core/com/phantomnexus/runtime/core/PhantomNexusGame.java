package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.phantomnexus.runtime.battle.AiController;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.DamagePopup;
import com.phantomnexus.runtime.battle.HitSpark;
import com.phantomnexus.runtime.battle.LandingDust;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.runtime.battle.Projectile;
import com.phantomnexus.runtime.battle.RoundManager;
import com.phantomnexus.runtime.audio.SoundManager;
import com.phantomnexus.runtime.debug.DebugOverlay;
import com.phantomnexus.runtime.debug.ReplayController;
import com.phantomnexus.runtime.debug.ScreenshotController;
import com.phantomnexus.runtime.input.Command;
import com.phantomnexus.runtime.input.CommandDetector;
import com.phantomnexus.runtime.input.GamepadInput;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.InputHistory;
import com.phantomnexus.runtime.input.PlayerInput;
import com.phantomnexus.runtime.rendering.FighterAnimator;
import com.phantomnexus.runtime.rendering.GameRenderer;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.schema.CharacterLoader;
import com.phantomnexus.shared.schema.StageLoader;
import com.phantomnexus.shared.types.BattleRules;
import com.phantomnexus.shared.types.AttackButton;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.GuardHeight;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Move;
import com.phantomnexus.shared.types.Stage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Phantom Nexus アプリケーション本体（ゲームループ / ライフサイクル）。
 *
 * <p>Core はライフサイクルと毎フレームの更新順序（入力 → 更新 → 描画）の制御に専念する。
 * 2 体の {@link Fighter} を保持し、{@link PlayerInput} の左右入力で移動・ジャンプ入力で跳躍させる
 * （P1=WASD / P2=方向キー、ジャンプは Task 8）。更新後に互いへ向き直し、{@link GameRenderer} へ描画委譲する。
 * キャラ / ステージ定義は外部 JSON から供給する（{@link CharacterLoader} / {@link StageLoader}、Task 16/17）。
 */
public class PhantomNexusGame extends ApplicationAdapter {

    private GameRenderer renderer;
    private SoundManager sounds;
    private boolean fightSoundPlayed; // "FIGHT!" バナー表示時の音を 1 回だけ再生するフラグ
    private PlayerInput p1Input;
    private PlayerInput p2Input;
    /** 接続済みコントローラー入力（gdx-controllers）。撮影/リプレイ再生では無効（決定性維持）。 */
    private GamepadInput gamepad;
    /** コントローラー入力が有効か（撮影モード・リプレイ再生では false＝poll もしない＝全クエリ false）。 */
    private boolean gamepadActive;
    private Fighter fighter1;
    private Fighter fighter2;
    private FighterAnimator animator1;
    private FighterAnimator animator2;
    private RoundManager round;
    private DebugOverlay debugOverlay;
    private final InputHistory history1 = new InputHistory();
    private final InputHistory history2 = new InputHistory();
    private Command lastCommand1 = Command.NONE;
    private Command lastCommand2 = Command.NONE;
    private int commandTimer1;
    private int commandTimer2;
    private final List<Projectile> projectiles = new ArrayList<>();
    // ダメージ数値ポップアップ（被弾 / ガード時の与ダメージ量を命中位置に浮かび上がらせる演出）。
    private final List<DamagePopup> damagePopups = new ArrayList<>();
    // ヒットスパーク（命中位置に出す火花の手応え演出。Task 38）。
    private final List<HitSpark> hitSparks = new ArrayList<>();
    // 着地の砂煙（滞空→接地の遷移で足元に出す土埃の演出。Task 131）。
    private final List<LandingDust> landingDusts = new ArrayList<>();
    // 着地検出用：前フレームの接地状態（false→true の遷移＝着地で砂煙を出す。Task 131）。
    private boolean p1WasGrounded = true;
    private boolean p2WasGrounded = true;
    // ダッシュ開始検出用：前フレームのダッシュ状態（false→true の遷移＝地上ダッシュ開始で砂煙を出す。Task 140）。
    private boolean p1WasDashing;
    private boolean p2WasDashing;
    private final AiController p2Ai = new AiController();
    private boolean p2AiEnabled = true; // P2 を AI 制御にするか（F2 でトグル。Task 21）
    private String controlsHint;
    private ScreenshotController screenshot;
    private ReplayController replay;
    private float spawnX1;
    private float spawnX2;
    private int hitstopFrames; // ヒットストップ（命中時に両者を凍結する残りフレーム・Task 86）
    private int superFlashFrames; // スーパーフラッシュ（スーパー必殺技発動時に両者を凍結する残りフレーム・Task 108）
    private int koSlowFrames; // KO スローモーション（決着の一撃後のスロー再生・残り実フレーム・Task 115）
    private boolean koSlowTriggered; // このラウンドで KO スローを既に開始したか（1 ラウンド 1 回・Task 115）
    private boolean trainingMode; // トレーニングモード（HP 無限のダミーでコンボ練習・F4 トグル・Task 90）
    private boolean moveListVisible; // コマンド表 HUD（技/コマンド一覧・F5 トグル・Task 112）
    // リマッチ用の直前マッチ情報（Task 178）。startBattle/startTraining で記録し、R キーで同設定の再戦に使う。
    private String rematchP1Id;
    private String rematchP2Id;
    private String rematchStageId;
    private boolean rematchTraining; // 直前がトレーニングモードだったか（リマッチ時に同モードで開始する）

    /** 画面状態（Task 116/117/128/182/188）。通常起動はタイトルから。撮影/リプレイは後方互換のため BATTLE 直行。 */
    enum Screen { TITLE, CHARACTER_SELECT, STAGE_SELECT, BATTLE, KEY_CONFIG, CHARACTER_VIEWER }
    private Screen screen = Screen.BATTLE; // 既定 BATTLE（撮影/リプレイ・後方互換）。通常起動は create() で TITLE に。
    private int titleSelection; // タイトルのモード選択（0=対戦 / 1=トレーニング / 2=サバイバル / 3=タイムアタック / 4=キーコンフィグ / 5=ビューア / 6=リプレイ）

    // キャラクタービューア（Task 182）
    private int viewerCharIndex;  // 現在表示中のキャラ index
    private int viewerStateIndex; // 現在表示中のアニメーション状態 index
    private int viewerFrame;      // 現在表示中のフレーム index
    private int viewerFrameTick;  // フレーム自動送りカウンタ
    private static final int VIEWER_FRAME_DELAY = 10; // 自動送り速度（10f≒6fps@60fps）

    // キーコンフィグ画面（Task 188）
    /** 設定中のアクション行（0〜7）。 */
    private int keyConfigRow;
    /** 設定中のプレイヤー列（0=P1 / 1=P2）。 */
    private int keyConfigPlayer;
    /** キー入力待ち中か（true のときは次の任意キー押下を割り当てる）。 */
    private boolean keyConfigWaiting;
    /** 入力待ちの対象アクション。 */
    private InputAction keyConfigAction;
    /** 定義順のアクション一覧（キーコンフィグの表示順）。 */
    private static final InputAction[] KEY_CONFIG_ACTIONS = InputAction.values();

    // インゲーム入力リプレイ（Task 183）：バトル中の入力を in-memory バッファに記録し、マッチ終了時に自動保存する。
    /** 直前バトルの入力フレームバッファ（{p1mask, p2mask, ai}）。 */
    private final List<int[]> ingameReplayBuffer = new ArrayList<>();
    /** 直前バトルを replays/last.rep へ保存済みか（1 マッチ 1 回のみ）。 */
    private boolean ingameSaved;
    /** replays/last.rep が存在し「REPLAY LAST」が有効か。 */
    private boolean replayAvailable;
    /** インゲームリプレイ再生中の ReplayController（null は非再生）。 */
    private ReplayController ingameReplay;

    // サバイバルモード（Task 184）：HP 引き継ぎで連続対戦。
    /** サバイバルモード中か。 */
    private boolean survivalMode;
    /** 倒した対戦相手の数。 */
    private int survivalKills;
    /** ラウンド終了後に P1 へ引き継ぐ HP（勝利確定のフレームで記録）。 */
    private int survivalP1Hp;
    /** サバイバルモードで次の対戦相手を自動開始する直前の確認フラグ（1 マッチ 1 回）。 */
    private boolean survivalNextScheduled;

    // タイムアタックモード（Task 185）：制限時間内に何人倒せるか。
    /** 制限時間（フレーム数）。60fps × 60 秒 = 3600f。 */
    private static final int TIME_ATTACK_TOTAL_FRAMES = 60 * 60;
    /** タイムアタックモード中か。 */
    private boolean timeAttackMode;
    /** 残りフレーム数（0 でタイムアップ）。 */
    private int timeAttackFramesLeft;
    /** タイムアタックで倒した対戦相手の数。 */
    private int timeAttackKills;

    // アーケードハイスコアランキング（Task 187）：セッション内のサバイバル/タイムアタックの上位 5 スコアを保持する。
    /** ランキングに保持する最大件数。 */
    private static final int HIGH_SCORE_MAX = 5;
    /** セッション内の上位スコアリスト（スコア降順・最大 HIGH_SCORE_MAX 件）。 */
    private final java.util.ArrayList<Integer> highScores = new java.util.ArrayList<>();
    /** 現在のアーケードゲームオーバーでスコアをランキングに登録済みか（二重登録防止）。 */
    private boolean arcadeScoreRecorded;

    /** キャラクター選択（Task 117）のロスター（全キャラ ID）。新キャラを足したらここにも追記する。 */
    private static final String[] ROSTER_IDS = {
        "fighter001", "fighter002", "fighter003", "fighter004", "fighter005",
        "fighter006", "fighter007", "fighter008", "fighter009", "fighter010"
    };
    /** キャラクター選択グリッドの列数（Task 117）。10体＝5列×2行。 */
    private static final int ROSTER_COLS = 5;
    private String[] rosterNames;   // ロスターの表示名（遅延ロード・charselect に入ったとき構築・Task 117）
    private Character[] rosterDefs;  // ロスターのキャラ定義（選択画面でスプライト立ち絵を描くためキャッシュ・Task 161）
    private int charCursor;         // 選択カーソルの現在 index（Task 117）
    private int charSelP1 = -1;     // P1 が確定したキャラ index（未確定 -1・Task 117）
    private int charSelP2 = -1;     // P2 が確定したキャラ index（未確定 -1・Task 117）
    private boolean charP1Locked;   // P1 が確定して P2 選択中か（Task 117）
    private BattleRules battleRules; // 対戦からの再開（charselect→battle）でラウンドを作り直すため保持（Task 117）
    private int introFramesValue;    // 同上（ラウンド開始イントロ長・Task 117）

    /** ステージ選択（Task 128）の全ステージ ID。新ステージを足したらここにも追記する。 */
    private static final String[] STAGE_IDS = {
        "stage001", "stage002", "stage003", "stage004", "stage005",
        "stage006", "stage007", "stage008", "stage009", "stage010"
    };
    /** ステージ選択グリッドの列数（Task 128）。 */
    private static final int STAGE_COLS = 5;
    private String[] stageNames;    // ステージの表示名（遅延ロード・stageselect に入ったとき構築・Task 128）
    private Stage[] stagePreviews;  // ステージ選択のライブプレビュー用にロード済み Stage をキャッシュ（Task 159）
    private int stageCursor;        // ステージ選択カーソルの現在 index（Task 128）
    private int titleBgIndex;       // タイトル裏デモリール背景の現在ステージ index（Task 160）
    private int titleBgTimer;       // 同・次ステージへ切り替えるまでのフレームカウンタ（Task 160）
    private static final int TITLE_BG_CYCLE_FRAMES = 300; // タイトル背景の 1 ステージ表示時間（≒5 秒@60fps・Task 160）
    private final List<String> p1Inputs = new ArrayList<>(); // 入力表示 HUD 用の P1 直近入力ログ（Task 96）
    private String lastInputToken = ""; // 入力ログへの重複追加を防ぐ直近トークン（Task 96）
    private static final int INPUT_LOG_MAX = 14; // 入力表示に残す最大トークン数（Task 96）

    /** 検出コマンドを HUD に表示し続けるフレーム数。 */
    private static final int COMMAND_DISPLAY_FRAMES = 90;

    /**
     * ライフサイクル初期化（LibGDX が GL コンテキスト確立後に 1 回呼ぶ）。描画・撮影コントローラを生成し、
     * ステージ / キャラクター定義を外部 JSON から読み込み、ファイター・アニメ・ラウンド管理・AI を構築する。
     * 撮影モードのオーバーライド（ステージ / 初期 X / 制限時間 / AI 等）を参照するため `ScreenshotController` を先に初期化する。
     */
    @Override
    public void create() {
        renderer = new GameRenderer();
        sounds = new SoundManager();
        // ヘッドレス自動スクショ（phantom.screenshot.* 指定時のみ有効。通常起動には無影響）。
        // ステージ/キャラ等のオーバーライドを参照するため、他のロードより先に初期化する。
        screenshot = new ScreenshotController();
        // ステージを外部 JSON から読み込み、背景描画に設定する（Task 17）。
        // 既定 stage001。撮影時は stage=<id> でオーバーライド可能（背景の撮り分け用。Task 40）。
        Stage stage = StageLoader.load(screenshot.stageId("stage001"));
        renderer.setStage(stage);
        p1Input = PlayerInput.player1Defaults();
        p2Input = PlayerInput.player2Defaults();
        // 保存済みキー割当を読み込む（Task 189）。ファイルがなければデフォルト割当を維持。
        SettingsManager.load(p1Input, p2Input);
        // 過渡状態の撮影用に、指定があれば起動時から入力を押下状態に固定する（通常は空＝無影響）。
        p1Input.setForcedHold(screenshot.heldActions(1));
        p2Input.setForcedHold(screenshot.heldActions(2));
        // 外部 JSON からキャラクター定義を読み込む（Task 16）。データ I/O は Shared/Schema が単一の真実。
        // 既定は fighter001 / fighter002。撮影時は p1char=/p2char=<id> でオーバーライド可能（新キャラの撮影用。Task 41）。
        Character aoi = CharacterLoader.load(screenshot.charId(1, "fighter001"));
        Character akane = CharacterLoader.load(screenshot.charId(2, "fighter002"));
        // 撮影モード時は初期 X をオーバーライド可能（近接が必要な被弾スクショ等の再現用）。
        spawnX1 = screenshot.spawnX(1, GameConstants.P1_SPAWN_X);
        spawnX2 = screenshot.spawnX(2, GameConstants.P2_SPAWN_X);
        fighter1 = new Fighter(aoi, spawnX1, true);
        fighter2 = new Fighter(akane, spawnX2, false);
        // 撮影時は初期必殺技ゲージをオーバーライド可能（EX 必殺技の見え方を貯め直しなしで撮る用。Task 44）。
        fighter1.setMeter(screenshot.initialMeter(1, 0f));
        fighter2.setMeter(screenshot.initialMeter(2, 0f));
        // 撮影時は初期 HP もオーバーライド可能（低 HP 警告ビネット・KO 演出・勝者グローを削り合わず撮る用。Task 145〜）。
        fighter1.setCurrentHp(screenshot.initialHp(1, fighter1.getMaxHp()));
        fighter2.setCurrentHp(screenshot.initialHp(2, fighter2.getMaxHp()));
        // アニメーション状態機械（Task 9）。各ファイターの実行時状態から idle/walk/jump を導出する。
        animator1 = new FighterAnimator();
        animator2 = new FighterAnimator();
        // 対戦ルール / ラウンド管理（Task 14 / Task 26）。撮影時は制限時間をオーバーライド可能（結果表示の撮影用）。
        battleRules = new BattleRules(
                screenshot.timeLimitSeconds(BattleRules.defaults().getTimeLimitSeconds()),
                BattleRules.defaults().getRoundsToWin());
        // ラウンド開始イントロ（"ROUND N"/"FIGHT!"・Task 42）。通常起動は有効。撮影モードは既定でスキップし
        // （既存スクショレシピの後方互換）、intro=true 指定時のみ有効化して開始演出を撮れる。
        introFramesValue = screenshot.roundIntroEnabled(true)
                ? GameConstants.ROUND_INTRO_FRAMES : 0;
        round = new RoundManager(battleRules, introFramesValue);
        // デバッグ当たり判定表示（Task 18）。既定 OFF・F1 でトグル。撮影時は debug=true で強制 ON。
        debugOverlay = new DebugOverlay();
        debugOverlay.setEnabled(screenshot.debugEnabled());
        // P2 の AI（Task 21）。既定 ON・F2 でトグル。撮影時は ai=false で人間（静止）に切替可能。
        p2AiEnabled = screenshot.aiEnabled(true);
        // AI 難易度（Task 56）。既定 HARD（全反応＝従来挙動）。撮影時は aidiff=easy/normal/hard で差し替え。
        // 未指定（null）なら setDifficulty が無視して既定 HARD を保つ＝既存リプレイ/レシピの決定性を保つ。
        p2Ai.setDifficulty(AiController.Difficulty.fromToken(screenshot.aiDifficulty(null)));
        // トレーニングモード（Task 90）。撮影は training=true で起動時 ON。ON のときダミー（P2）の AI を切る。
        trainingMode = screenshot.trainingEnabled(false);
        if (trainingMode) {
            p2AiEnabled = false;
        }
        moveListVisible = screenshot.moveListEnabled(false); // コマンド表 HUD（Task 112）。撮影は movelist=true で起動時 ON。
        controlsHint = buildControlsHint();
        // 入力リプレイ（記録 / 再生）。phantom.replay.* 指定時のみ有効。通常起動には無影響。
        replay = new ReplayController();
        if (replay.isRecording()) {
            // 記録モードは開始 AI 状態を上書き可能（phantom.replay.ai=false で静止相手に記録）。
            p2AiEnabled = replay.startAiEnabled(p2AiEnabled);
            controlsHint = "[REC]  " + controlsHint;
        } else if (replay.isReplaying()) {
            controlsHint = "[REPLAY] " + replay.frameCount() + "f   [F1] hitboxes";
        }
        // ゲームパッド入力：PC にコントローラーが繋がっていればそれで操作できる（1P=スロット0 / 2P=スロット1）。
        // 撮影モード・リプレイ再生では無効化する（強制入力 / 記録済み入力のみで決定的に再現するため）。
        // 記録モード（isRecording）では有効＝コントローラー操作も記録される。
        // gamepadActive=false のときは render() で poll() せず、メニュー / 攻撃いずれのクエリも全て false に縮退する
        // （poll が唯一の状態更新点なので、ポーリングしなければエッジ / 押下集合は空のまま＝完全に無効）。
        gamepad = new GamepadInput();
        gamepadActive = !screenshot.isEnabled() && !replay.isReplaying();
        if (gamepadActive) {
            p1Input.attachGamepad(gamepad, 0);
            p2Input.attachGamepad(gamepad, 1);
        }
        // インゲームリプレイ（Task 183）：起動時に保存ファイルが存在すれば「REPLAY LAST」を有効化する。
        replayAvailable = new java.io.File(ingameReplayPath()).exists();
        // 画面状態の初期化（Task 116/117）：通常起動はタイトル画面から始める。撮影モード・リプレイは
        // 後方互換のため BATTLE 直行（既存スクショレシピ・リプレイは frame1 から戦闘開始の前提）。
        // 撮影で各画面を撮るときだけ -x startscreen=title/charselect で上書きできる（既定 battle）。
        if (replay.isRecording() || replay.isReplaying()) {
            screen = Screen.BATTLE;
        } else if (screenshot.isEnabled()) {
            screen = parseStartScreen(screenshot.startScreen("battle"));
            // 撮影で STAGE_SELECT 直行（-x startscreen=stageselect）するときは、通常フローと違いキャラ選択を
            // 経由しないため charSelP1/charSelP2 が未設定（-1）のまま。確定（startBattle）が ROSTER_IDS[-1] で
            // 落ちないよう、create() でロード済みのファイターから index を復元して補完する（CodeRabbit 指摘）。
            if (screen == Screen.STAGE_SELECT) {
                seedCharacterSelectionFromCurrentFighters();
            }
            if (screen == Screen.CHARACTER_VIEWER) {
                // 撮影で VIEWER 直行するときもロスターを先にロードする（updateCharacterViewer より前）。
                ensureRosterLoaded();
            }
        } else {
            screen = Screen.TITLE;
        }
    }

    /**
     * 現在ロード済みのファイター（{@code fighter1}/{@code fighter2}）の ID から {@code charSelP1}/{@code charSelP2} を
     * 復元する（Task 128）。キャラ選択を経由しない STAGE_SELECT 直行（撮影 {@code -x startscreen=stageselect}）で
     * 確定したときに、選んだステージ＋現在のキャラでバトルを開始できるようにする補完。ロスターに無い ID は
     * 既定（P1=0 / P2=1）にフォールバックする。
     */
    private void seedCharacterSelectionFromCurrentFighters() {
        ensureRosterLoaded();
        charSelP1 = 0;
        charSelP2 = 1;
        String p1Id = fighter1.getDef().getId();
        String p2Id = fighter2.getDef().getId();
        for (int i = 0; i < ROSTER_IDS.length; i++) {
            if (ROSTER_IDS[i].equals(p1Id)) {
                charSelP1 = i;
            }
            if (ROSTER_IDS[i].equals(p2Id)) {
                charSelP2 = i;
            }
        }
        charP1Locked = true;
    }

    /** 撮影オーバーライドの開始画面トークンを {@link Screen} へ解釈する（Task 116/117/128。既定/未知は BATTLE）。 */
    private static Screen parseStartScreen(String token) {
        if (token == null) {
            return Screen.BATTLE;
        }
        switch (token.trim().toLowerCase()) {
            case "title":
                return Screen.TITLE;
            case "charselect":
                return Screen.CHARACTER_SELECT;
            case "stageselect":
                return Screen.STAGE_SELECT;
            case "viewer":
                return Screen.CHARACTER_VIEWER;
            case "keyconfig":
                return Screen.KEY_CONFIG;
            default:
                return Screen.BATTLE;
        }
    }

    /**
     * タイトル画面の入力処理（Task 116）。上下でモード選択（0=対戦 / 1=トレーニング）、Enter/Space/J で確定。
     * 対戦＝P2 AI ON・トレーニング＝P2 が何もしない（AI OFF）＋ HP 無限練習。確定で BATTLE へ遷移する
     * （Task 117 で対戦は CHARACTER_SELECT を経由するよう拡張予定）。メニューは Gdx キーを直接見る（純 UI・乱数なし）。
     */
    private void updateTitle() {
        // 0=VERSUS / 1=TRAINING / 2=SURVIVAL / 3=TIME ATTACK / 4=KEY CONFIG / 5=VIEWER / 6=REPLAY LAST（ファイルあり時のみ）。
        final int TITLE_ITEMS = replayAvailable ? 7 : 6;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || gamepad.menuUp()) {
            titleSelection = (titleSelection - 1 + TITLE_ITEMS) % TITLE_ITEMS;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || gamepad.menuDown()) {
            titleSelection = (titleSelection + 1) % TITLE_ITEMS;
        }
        // カーソルが無効な項目を指していたらクランプ（リプレイが無効化された後の保護）。
        if (titleSelection >= TITLE_ITEMS) {
            titleSelection = TITLE_ITEMS - 1;
        }
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.J)
                || gamepad.menuConfirm();
        if (confirm) {
            if (titleSelection == 1) {
                // トレーニング：P2 は何もしない（AI OFF）＋ HP 無限でコンボ練習。
                startTraining();
            } else if (titleSelection == 2) {
                // サバイバル：HP を引き継いで連続対戦（Task 184）。
                startSurvival();
            } else if (titleSelection == 3) {
                // タイムアタック：制限時間内に何人倒せるか（Task 185）。
                startTimeAttack();
            } else if (titleSelection == 4) {
                // キーコンフィグ：P1/P2 のキー割当を変更する（Task 188）。
                enterKeyConfig();
            } else if (titleSelection == 5) {
                // キャラクタービューア（Task 182）：アニメーションを閲覧する画面へ遷移。
                enterCharacterViewer();
            } else if (titleSelection == 6 && replayAvailable) {
                // REPLAY LAST：保存済みの直前バトルを再生する（Task 183）。
                startIngameReplay();
            } else {
                // 対戦：P2 AI ON。キャラクター選択（Task 117）へ遷移する。
                trainingMode = false;
                p2AiEnabled = true;
                enterCharacterSelect();
            }
        }
    }

    /**
     * トレーニングモードでバトルを開始する（Task 116）。P2 AI を切り HP 無限の練習にする。
     * ラウンドとファイターを作り直してから BATTLE へ遷移するため、決着後にタイトルへ戻ってから選んでも
     * 前マッチの確定状態を持ち越さない（{@link #returnToTitle()} と組み合わせて正しく再開できる）。
     */
    private void startTraining() {
        trainingMode = true;
        p2AiEnabled = false;
        // リマッチ用にトレーニングモードと現在のキャラ情報を保存する（Task 178）
        rematchTraining = true;
        rematchP1Id = fighter1.getDef().getId();
        rematchP2Id = fighter2.getDef().getId();
        rematchStageId = null; // トレーニングはステージ固定
        round = new RoundManager(battleRules, introFramesValue);
        resetFighters();
        // インゲームリプレイ（Task 183）：バッファをクリアして新バトルの記録を開始する。
        ingameReplayBuffer.clear();
        ingameSaved = false;
        ingameReplay = null;
        controlsHint = buildControlsHint();
        screen = Screen.BATTLE;
    }

    /**
     * 決着後にスタート画面（タイトル）へ戻る。画面状態を TITLE に戻すだけで、再開時のバトル構築は
     * 各遷移（対戦＝キャラ/ステージ選択→{@link #startBattle}・トレーニング＝{@link #startTraining}）が
     * 改めてラウンド・ファイターを作り直すため、前マッチの確定状態は持ち越さない。通常プレイ専用。
     */
    private void returnToTitle() {
        survivalMode = false;
        timeAttackMode = false;
        renderer.setArcadeScore(-1);
        renderer.setHighScores(highScores);
        titleSelection = 0;
        screen = Screen.TITLE;
    }

    /** キャラクタービューア画面へ入る（Task 182）。ロスターを遅延ロードし、先頭キャラ・idle 状態で開始。 */
    private void enterCharacterViewer() {
        ensureRosterLoaded();
        viewerCharIndex = 0;
        viewerStateIndex = 0;
        viewerFrame = 0;
        viewerFrameTick = 0;
        screen = Screen.CHARACTER_VIEWER;
    }

    /**
     * キャラクタービューア画面の入力処理（Task 182）。
     * LEFT/RIGHT でキャラ切替、UP/DOWN でアニメーション状態切替、ESC でタイトルへ戻る。
     */
    private void updateCharacterViewer() {
        int n = ROSTER_IDS.length;
        com.phantomnexus.runtime.rendering.AnimationState[] states =
                com.phantomnexus.runtime.rendering.AnimationState.values();

        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)
                || gamepad.menuRight()) {
            viewerCharIndex = (viewerCharIndex + 1) % n;
            viewerFrame = 0;
            viewerFrameTick = 0;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)
                || gamepad.menuLeft()) {
            viewerCharIndex = (viewerCharIndex - 1 + n) % n;
            viewerFrame = 0;
            viewerFrameTick = 0;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || gamepad.menuDown()) {
            viewerStateIndex = (viewerStateIndex + 1) % states.length;
            viewerFrame = 0;
            viewerFrameTick = 0;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || gamepad.menuUp()) {
            viewerStateIndex = (viewerStateIndex - 1 + states.length) % states.length;
            viewerFrame = 0;
            viewerFrameTick = 0;
        }
        // アニメーションの自動フレーム送り
        viewerFrameTick++;
        if (viewerFrameTick >= VIEWER_FRAME_DELAY) {
            viewerFrameTick = 0;
            com.phantomnexus.runtime.rendering.AnimationState state = states[viewerStateIndex];
            viewerFrame = (viewerFrame + 1) % Math.max(1, state.frameCount());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)
                || gamepad.menuCancel()) {
            screen = Screen.TITLE;
        }
    }

    /** キーコンフィグ画面へ遷移する（Task 188）。 */
    private void enterKeyConfig() {
        keyConfigRow = 0;
        keyConfigPlayer = 0;
        keyConfigWaiting = false;
        keyConfigAction = null;
        screen = Screen.KEY_CONFIG;
    }

    /**
     * キーコンフィグ画面の入力処理（Task 188）。
     * 通常モード: 上下でアクション行選択・左右で P1/P2 切替・ENTER で入力待ち・ESC でタイトルへ戻る。
     * 入力待ちモード: 次の任意キー押下を割り当て・ESC でキャンセル。
     */
    private void updateKeyConfig() {
        if (keyConfigWaiting) {
            // 何かキーが押されたらその keycode を割り当てる。ESC でキャンセル。
            int pressedKey = -1;
            // Gdx.input.isKeyJustPressed は全キーを順に調べる必要があるため Input.Keys の範囲を走査する。
            for (int k = 0; k <= Input.Keys.MAX_KEYCODE; k++) {
                if (Gdx.input.isKeyJustPressed(k)) {
                    pressedKey = k;
                    break;
                }
            }
            if (pressedKey == Input.Keys.ESCAPE) {
                // ESC はキャンセル（元の割当を保持）。
                keyConfigWaiting = false;
                keyConfigAction = null;
            } else if (pressedKey >= 0) {
                // 割り当て適用して設定ファイルへ保存する（Task 189）。
                if (keyConfigPlayer == 0) {
                    p1Input.setBinding(keyConfigAction, pressedKey);
                } else {
                    p2Input.setBinding(keyConfigAction, pressedKey);
                }
                SettingsManager.save(p1Input, p2Input);
                keyConfigWaiting = false;
                keyConfigAction = null;
            }
            return;
        }
        // 通常モード: ナビゲーション。
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            keyConfigRow = (keyConfigRow - 1 + KEY_CONFIG_ACTIONS.length) % KEY_CONFIG_ACTIONS.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            keyConfigRow = (keyConfigRow + 1) % KEY_CONFIG_ACTIONS.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            keyConfigPlayer = 0;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            keyConfigPlayer = 1;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.J)) {
            keyConfigAction = KEY_CONFIG_ACTIONS[keyConfigRow];
            keyConfigWaiting = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            screen = Screen.TITLE;
        }
    }

    /**
     * スコアをセッションランキングに登録し、降順上位 HIGH_SCORE_MAX 件を保持する（Task 187）。
     * 挿入後にスコア一覧をレンダラへ渡して結果バナーに反映する。
     */
    private void recordHighScore(int score) {
        highScores.add(score);
        highScores.sort(java.util.Collections.reverseOrder());
        while (highScores.size() > HIGH_SCORE_MAX) {
            highScores.remove(highScores.size() - 1);
        }
        renderer.setHighScores(highScores);
    }

    /**
     * 保存済みリプレイ（replays/last.rep）を読み込んでバトル再生を開始する（Task 183）。
     * ファイルが読めなければ何もしない（replayAvailable は false のまま）。
     */
    private void startIngameReplay() {
        ReplayController rc = ReplayController.forPlayback(ingameReplayPath());
        if (!rc.isReplaying()) {
            return;
        }
        ingameReplay = rc;
        // 再生中はバトル用の fighter/round を作り直す（create() 時のデフォルトキャラ/ステージをそのまま使う）。
        round = new RoundManager(battleRules, 0 /* イントロなし */);
        resetFighters();
        controlsHint = "[REPLAY] " + rc.frameCount() + "f   [F1] hitboxes";
        screen = Screen.BATTLE;
    }

    /** インゲームリプレイの保存パス。 */
    private static String ingameReplayPath() {
        return "replays/last.rep";
    }

    /**
     * サバイバルモードを開始する（Task 184）。P1 は既定キャラ・フル HP でスタートし、
     * AI 相手にランダムキャラで連続対戦する。HP は試合間で引き継ぎ、負けた時点で終了。
     */
    private void startSurvival() {
        survivalMode = true;
        survivalKills = 0;
        arcadeScoreRecorded = false;
        survivalP1Hp = fighter1.getMaxHp();
        survivalNextScheduled = false;
        trainingMode = false;
        p2AiEnabled = true;
        // P2 をランダムキャラで差し替える（P1 は create() ロード済みのデフォルトキャラを流用）。
        String p2Id = ROSTER_IDS[(int)(Math.random() * ROSTER_IDS.length)];
        String stageId = STAGE_IDS[(int)(Math.random() * STAGE_IDS.length)];
        ensureStagesLoaded();
        renderer.setStage(stagePreviews[(int)(Math.random() * stagePreviews.length)]);
        fighter2 = new Fighter(CharacterLoader.load(p2Id), spawnX2, false);
        fighter2.setMeter(0f);
        animator2 = new FighterAnimator();
        round = new RoundManager(new BattleRules(battleRules.getTimeLimitSeconds(), 1), 0);
        resetFighters();
        fighter1.setCurrentHp(survivalP1Hp); // フル HP（初戦）
        controlsHint = buildControlsHint();
        ingameReplayBuffer.clear();
        ingameSaved = false;
        ingameReplay = null;
        screen = Screen.BATTLE;
    }

    /**
     * サバイバルモードで次の対戦相手へ進む（P1 が勝った場合・Task 184）。
     * P1 の HP を引き継ぎ、新しいランダムキャラ＋ランダムステージで 1 ラウンドバトルを開始する。
     */
    private void advanceSurvival() {
        survivalKills++;
        survivalP1Hp = fighter1.getCurrentHp();
        survivalNextScheduled = false;
        String p2Id = ROSTER_IDS[(int)(Math.random() * ROSTER_IDS.length)];
        renderer.setStage(stagePreviews[(int)(Math.random() * stagePreviews.length)]);
        fighter2 = new Fighter(CharacterLoader.load(p2Id), spawnX2, false);
        fighter2.setMeter(0f);
        animator2 = new FighterAnimator();
        round = new RoundManager(new BattleRules(battleRules.getTimeLimitSeconds(), 1), 0);
        resetFighters();
        fighter1.setCurrentHp(survivalP1Hp); // HP 引き継ぎ
        ingameReplayBuffer.clear();
        ingameSaved = false;
        ingameReplay = null;
        controlsHint = buildControlsHint(); // kills カウントを反映
    }

    /**
     * タイムアタックモードを開始する（Task 185）。60 秒の制限時間内にランダム相手を何人倒せるか。
     * HP は引き継がず毎対戦フル HP でスタートし、P1 勝利で即次の相手（確認なし）。
     * タイムアップまたは P1 敗北でゲームオーバー。
     */
    private void startTimeAttack() {
        timeAttackMode = true;
        timeAttackKills = 0;
        arcadeScoreRecorded = false;
        timeAttackFramesLeft = TIME_ATTACK_TOTAL_FRAMES;
        trainingMode = false;
        p2AiEnabled = true;
        ensureStagesLoaded();
        String p2Id = ROSTER_IDS[(int)(Math.random() * ROSTER_IDS.length)];
        renderer.setStage(stagePreviews[(int)(Math.random() * stagePreviews.length)]);
        fighter2 = new Fighter(CharacterLoader.load(p2Id), spawnX2, false);
        fighter2.setMeter(0f);
        animator2 = new FighterAnimator();
        round = new RoundManager(new BattleRules(battleRules.getTimeLimitSeconds(), 1), 0);
        resetFighters();
        ingameReplayBuffer.clear();
        ingameSaved = false;
        ingameReplay = null;
        controlsHint = buildControlsHint();
        screen = Screen.BATTLE;
    }

    /**
     * タイムアタックモードで次の対戦相手へ即座に進む（P1 が勝った場合・Task 185）。
     * HP はリセット（毎戦フル HP）して新しいランダムキャラ＋ステージで 1 ラウンドバトルを続行する。
     */
    private void advanceTimeAttack() {
        timeAttackKills++;
        String p2Id = ROSTER_IDS[(int)(Math.random() * ROSTER_IDS.length)];
        renderer.setStage(stagePreviews[(int)(Math.random() * stagePreviews.length)]);
        fighter2 = new Fighter(CharacterLoader.load(p2Id), spawnX2, false);
        fighter2.setMeter(0f);
        animator2 = new FighterAnimator();
        round = new RoundManager(new BattleRules(battleRules.getTimeLimitSeconds(), 1), 0);
        resetFighters();
        ingameReplayBuffer.clear();
        ingameSaved = false;
        ingameReplay = null;
        controlsHint = buildControlsHint();
    }

    /** キャラクター選択画面へ入る（Task 117）。ロスター名を遅延ロードし、選択状態を初期化する。 */
    private void enterCharacterSelect() {
        ensureRosterLoaded();
        charCursor = 0;
        charSelP1 = -1;
        charSelP2 = -1;
        charP1Locked = false;
        screen = Screen.CHARACTER_SELECT;
    }

    /** ロスターの表示名を（未ロードなら）構築する。各キャラ JSON を読み名前を取り出す（Task 117）。 */
    private void ensureRosterLoaded() {
        if (rosterNames != null) {
            return;
        }
        rosterNames = new String[ROSTER_IDS.length];
        rosterDefs = new Character[ROSTER_IDS.length];
        for (int i = 0; i < ROSTER_IDS.length; i++) {
            Character def = CharacterLoader.load(ROSTER_IDS[i]);
            rosterDefs[i] = def;            // Task 161: 選択画面でスプライト立ち絵を描くためキャッシュ
            rosterNames[i] = def.getName();
        }
    }

    /**
     * キャラクター選択画面の入力処理（Task 117）。矢印/WASD でカーソル移動、ENTER/SPACE/J で確定。
     * 先に P1 が選び（確定で P1 ロック）、続いて P2 が選ぶ。両者確定で選んだキャラでバトルを開始する。
     * R キーでカーソルをランダム位置に移動（Task 179）。純 UI。
     */
    private void updateCharacterSelect() {
        int n = ROSTER_IDS.length;
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)
                || gamepad.menuRight()) {
            charCursor = (charCursor + 1) % n;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)
                || gamepad.menuLeft()) {
            charCursor = (charCursor - 1 + n) % n;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || gamepad.menuDown()) {
            charCursor = Math.min(n - 1, charCursor + ROSTER_COLS);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || gamepad.menuUp()) {
            charCursor = Math.max(0, charCursor - ROSTER_COLS);
        }
        // R キー：カーソルをランダム位置へ移動（ランダム選択のプレビュー・ENTER で確定・Task 179）
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            charCursor = (int) (Math.random() * n);
        }
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.J)
                || gamepad.menuConfirm();
        if (confirm) {
            if (!charP1Locked) {
                charSelP1 = charCursor;
                charP1Locked = true;
            } else {
                charSelP2 = charCursor;
                // 両者確定後はステージ選択（Task 128）へ遷移し、選んだステージでバトルを開始する。
                enterStageSelect();
            }
        }
    }

    /** ステージ選択画面へ入る（Task 128）。ステージ名を遅延ロードし、カーソルを先頭に戻す。 */
    private void enterStageSelect() {
        ensureStagesLoaded();
        stageCursor = 0;
        screen = Screen.STAGE_SELECT;
    }

    /** ステージの表示名を（未ロードなら）構築する。各ステージ JSON を読み名前を取り出す（Task 128）。 */
    private void ensureStagesLoaded() {
        if (stageNames != null) {
            return;
        }
        stageNames = new String[STAGE_IDS.length];
        stagePreviews = new Stage[STAGE_IDS.length];
        for (int i = 0; i < STAGE_IDS.length; i++) {
            Stage s = StageLoader.load(STAGE_IDS[i]);
            stagePreviews[i] = s;       // Task 159: 選択画面で実際の多層背景をライブプレビューするためキャッシュ
            stageNames[i] = s.getName();
        }
    }

    /**
     * ステージ選択画面の入力処理（Task 128）。矢印/WASD でカーソル移動、ENTER/SPACE/J で確定。
     * 確定で選んだステージを背景に設定し、確定済みの 2 キャラでバトルを開始する。純 UI・乱数なし。
     */
    private void updateStageSelect() {
        int n = STAGE_IDS.length;
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)
                || gamepad.menuRight()) {
            stageCursor = (stageCursor + 1) % n;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)
                || gamepad.menuLeft()) {
            stageCursor = (stageCursor - 1 + n) % n;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)
                || gamepad.menuDown()) {
            stageCursor = Math.min(n - 1, stageCursor + STAGE_COLS);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)
                || gamepad.menuUp()) {
            stageCursor = Math.max(0, stageCursor - STAGE_COLS);
        }
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.J)
                || gamepad.menuConfirm();
        if (confirm) {
            startBattle(ROSTER_IDS[charSelP1], ROSTER_IDS[charSelP2], STAGE_IDS[stageCursor]);
        }
    }

    /**
     * 選んだ 2 キャラ・ステージでバトルを開始する（Task 117 / Task 128）。背景を選択ステージへ差し替え、
     * ファイター・アニメ・ラウンド・AI を作り直して BATTLE へ遷移する。
     */
    private void startBattle(String p1Id, String p2Id, String stageId) {
        renderer.setStage(StageLoader.load(stageId)); // 選んだステージを背景に設定（Task 128）
        fighter1 = new Fighter(CharacterLoader.load(p1Id), spawnX1, true);
        fighter2 = new Fighter(CharacterLoader.load(p2Id), spawnX2, false);
        fighter1.setMeter(0f);
        fighter2.setMeter(0f);
        animator1 = new FighterAnimator();
        animator2 = new FighterAnimator();
        round = new RoundManager(battleRules, introFramesValue);
        p2Ai.reset();
        p1Inputs.clear();
        lastInputToken = "";
        // リマッチ用に直前のキャラ/ステージを保存する（Task 178）
        rematchP1Id = p1Id;
        rematchP2Id = p2Id;
        rematchStageId = stageId;
        rematchTraining = false;
        // インゲームリプレイ（Task 183）：バッファをクリアして新バトルの記録を開始する。
        ingameReplayBuffer.clear();
        ingameSaved = false;
        ingameReplay = null;
        controlsHint = buildControlsHint();
        screen = Screen.BATTLE;
    }

    /**
     * 直前と同じキャラ / ステージ / モードで再戦する（Task 178）。
     * 撮影/リプレイでは rematch 情報を設定しないため呼ばれない（後方互換）。
     */
    private void rematch() {
        if (rematchTraining) {
            startTraining();
        } else if (rematchP1Id != null) {
            trainingMode = false;
            p2AiEnabled = true;
            startBattle(rematchP1Id, rematchP2Id, rematchStageId);
        }
    }

    @Override
    public void render() {
        // ゲームパッドをフレーム先頭で 1 回ポーリングし、押下状態と立ち上がりエッジを更新する
        // （以降の入力読み取り・メニュー操作がこのフレームの値を参照する）。未接続/未対応環境では no-op。
        // 撮影モード・リプレイ再生（gamepadActive=false）では poll しない＝メニュー含む全クエリが false に
        // 縮退し、コントローラーが繋がっていても決定的再現を一切乱さない（CodeRabbit 指摘）。
        if (gamepadActive) {
            gamepad.poll();
        }
        // タイトル画面（Task 116）：モード選択（対戦 / トレーニング）。撮影/リプレイでは create() で BATTLE 直行のため
        // 通常はここに来ない（-x startscreen=title 指定時のみ撮影でも表示）。選択確定で対戦/トレーニングへ遷移する。
        if (screen == Screen.TITLE) {
            updateTitle();
            // タイトル裏のデモリール背景（Task 160）：全ステージの実背景をゆっくり巡回表示する。
            ensureStagesLoaded();
            titleBgTimer++;
            if (titleBgTimer >= TITLE_BG_CYCLE_FRAMES) {
                titleBgTimer = 0;
                titleBgIndex = (titleBgIndex + 1) % stagePreviews.length;
            }
            renderer.renderTitle(titleSelection, stagePreviews[titleBgIndex], replayAvailable);
            screenshot.maybeCapture();
            return;
        }
        // キーコンフィグ画面（Task 188）：タイトルの KEY CONFIG から遷移。ESC でタイトルへ戻る。
        if (screen == Screen.KEY_CONFIG) {
            updateKeyConfig();
            if (screen == Screen.KEY_CONFIG) {
                ensureStagesLoaded();
                renderer.renderKeyConfig(p1Input, p2Input, KEY_CONFIG_ACTIONS,
                        keyConfigRow, keyConfigPlayer, keyConfigWaiting, keyConfigAction,
                        stagePreviews != null && stagePreviews.length > 0 ? stagePreviews[0] : null);
                screenshot.maybeCapture();
            }
            return;
        }
        // キャラクター選択画面（Task 117）：対戦モードで遷移。P1→P2 の順にロスターから選び、両者確定でステージ選択へ。
        // 撮影で表示するときは create() で -x startscreen=charselect 指定（ロスター名を先にロードしておく）。
        // このフレームで遷移しても必ず return する（タイトル画面と同じ作法）。さもないと確定入力（ENTER 等）が
        // 同一 render() 内で後続のステージ選択にも再消費され、ステージを選ばず即バトルしてしまう（Codex 指摘）。
        if (screen == Screen.CHARACTER_SELECT) {
            ensureRosterLoaded();
            ensureStagesLoaded(); // Task 161: 選択画面の背景に使う（タイトル/ステージ選択と同じ実背景）
            updateCharacterSelect();
            if (screen == Screen.CHARACTER_SELECT) { // ステージ選択へ遷移していなければ描画
                renderer.renderCharacterSelect(rosterDefs, rosterNames, charCursor, charSelP1, charSelP2,
                        charP1Locked, ROSTER_COLS, stagePreviews[0]);
                screenshot.maybeCapture();
            }
            return; // 遷移したフレームはここで終了し、次フレームから新画面を処理（確定入力の再消費を防ぐ）。
        }
        // キャラクタービューア画面（Task 182）：タイトル VIEWER 選択で遷移。キャラ/状態を閲覧し ESC でタイトルへ戻る。
        if (screen == Screen.CHARACTER_VIEWER) {
            updateCharacterViewer();
            if (screen == Screen.CHARACTER_VIEWER) { // タイトルへ遷移していなければ描画
                com.phantomnexus.runtime.rendering.AnimationState[] states =
                        com.phantomnexus.runtime.rendering.AnimationState.values();
                renderer.renderCharacterViewer(rosterDefs, rosterNames, viewerCharIndex,
                        states[viewerStateIndex], viewerFrame);
                screenshot.maybeCapture();
            }
            return;
        }
        // ステージ選択画面（Task 128）：キャラ確定後に遷移。全ステージから選んで確定でバトル開始。
        // 撮影で表示するときは create() で -x startscreen=stageselect 指定（ステージ名を先にロードしておく）。
        // キャラ選択と同様、遷移しても必ず return して確定入力をバトル開始フレームへ持ち越さない。
        if (screen == Screen.STAGE_SELECT) {
            ensureStagesLoaded();
            updateStageSelect();
            if (screen == Screen.STAGE_SELECT) { // バトルへ遷移していなければ描画
                renderer.renderStageSelect(stagePreviews[stageCursor], stageNames, stageCursor, STAGE_COLS);
                screenshot.maybeCapture();
            }
            return; // 遷移したフレームはここで終了し、次フレームからバトルを処理。
        }
        // 決着後（マッチ確定）にスタート画面（タイトル）へ戻る。通常プレイのみ有効で、撮影/リプレイは
        // 結果を凍結したまま（既存スクショレシピ・リプレイの決定性を壊さない後方互換）。メニューと同じ
        // ENTER/SPACE/J、加えて ESC で戻れる。遷移したフレームは return して次フレームからタイトルを処理する。
        if (round.isFinished() && !screenshot.isEnabled()
                && !replay.isReplaying() && !replay.isRecording()) {
            // タイムアタックモード（Task 185）：P1 勝利なら即次の相手へ（確認不要）。タイムアップ/敗北は GAME OVER。
            if (timeAttackMode) {
                com.phantomnexus.runtime.battle.RoundManager.Winner taWinner = round.getMatchWinner();
                if (taWinner == com.phantomnexus.runtime.battle.RoundManager.Winner.P1) {
                    // P1 勝利：即次の対戦相手へ進む（ENTER 確認不要で自動進行）。
                    advanceTimeAttack();
                    return;
                } else {
                    // 敗北 / タイムアップ：ENTER/ESC でタイトルへ。
                    if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                            || Gdx.input.isKeyJustPressed(Input.Keys.J)
                            || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                            || gamepad.menuConfirm() || gamepad.menuCancel()) {
                        timeAttackMode = false;
                        returnToTitle();
                        return;
                    }
                }
            }
            // サバイバルモード（Task 184）：P1 勝利なら次の対戦相手へ自動進行（ENTER で続行）。
            // P2 勝利 or 引き分けなら GAME OVER として通常の「タイトルへ戻る」導線へ落とす。
            if (survivalMode) {
                com.phantomnexus.runtime.battle.RoundManager.Winner winner = round.getMatchWinner();
                if (winner == com.phantomnexus.runtime.battle.RoundManager.Winner.P1
                        && !survivalNextScheduled) {
                    // ENTER/SPACE/J で次の対戦相手へ進む。
                    if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                            || Gdx.input.isKeyJustPressed(Input.Keys.J)
                            || gamepad.menuConfirm()) {
                        survivalNextScheduled = true;
                        advanceSurvival();
                        return;
                    }
                } else if (winner != com.phantomnexus.runtime.battle.RoundManager.Winner.P1) {
                    // 敗北 / 引き分け：サバイバル終了。ESC/ENTER でタイトルへ。
                    if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                            || Gdx.input.isKeyJustPressed(Input.Keys.J)
                            || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                            || gamepad.menuConfirm() || gamepad.menuCancel()) {
                        survivalMode = false;
                        returnToTitle();
                        return;
                    }
                }
            }
            // R キー：リマッチ（同じキャラ/ステージ/モードで即再戦・Task 178）
            if (!survivalMode && !timeAttackMode && (Gdx.input.isKeyJustPressed(Input.Keys.R) || gamepad.menuLeft())
                    && rematchP1Id != null) {
                rematch();
                return;
            }
            // ENTER/SPACE/J/ESC：タイトルへ戻る（従来動作）
            if (!survivalMode && !timeAttackMode && (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                    || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                    || Gdx.input.isKeyJustPressed(Input.Keys.J)
                    || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                    || gamepad.menuConfirm() || gamepad.menuCancel())) {
                returnToTitle();
                return;
            }
        }
        // 撮影用タイムド入力スクリプト（コマンド技の再現）。毎フレーム先頭で押下を更新する。
        screenshot.applyTimedHolds(p1Input, p2Input);
        // 再生モード：記録済み入力をこのフレームの押下として注入し、P2 AI 状態も復元する。
        if (replay.isReplaying()) {
            replay.applyReplayFrame(p1Input, p2Input);
            p2AiEnabled = replay.replayAi(p2AiEnabled);
        }
        // デバッグ表示 / AI のトグル（グローバルキー。プレイヤー入力とは別系統のため Gdx を直接参照）。
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debugOverlay.toggle();
        }
        // 再生中は F2 を無視（記録した AI 状態を尊重し、視聴者のキーで試合が変わらないようにする）。
        if (!replay.isReplaying() && Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            p2AiEnabled = !p2AiEnabled;
        }
        // F3：AI 難易度を実行時に循環（EASY→NORMAL→HARD・Task 78）。リプレイ記録/再生中は難易度を per-frame に
        // 記録しない（format 不変・決定性維持）ため無視する。通常プレイのみ切替可能で、HUD ラベルを更新する。
        if (!replay.isRecording() && !replay.isReplaying() && Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            p2Ai.cycleDifficulty();
            controlsHint = buildControlsHint();
        }
        // F4：トレーニングモード（HP 無限のダミーでコンボ練習・Task 90）。ON にすると P2 AI を切る。
        // リプレイ記録/再生中は無視（HP の挙動が変わり決定性に影響するため）。
        if (!replay.isRecording() && !replay.isReplaying() && Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            trainingMode = !trainingMode;
            if (trainingMode) {
                p2AiEnabled = false;
            }
            controlsHint = buildControlsHint();
        }
        // コマンド表 HUD（Task 112）：F5 で技/コマンド一覧の表示を切り替える（純表示なので記録/再生中も操作可）。
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            moveListVisible = !moveListVisible;
            controlsHint = buildControlsHint();
        }
        // M キー：SE のミュート/アンミュートを切り替える（純演出なので記録/再生中も操作可）。
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            sounds.toggle();
            controlsHint = buildControlsHint();
        }
        // 記録モード：update が消費する前にこのフレームの入力スナップショットを残す。
        if (replay.isRecording()) {
            replay.recordFrame(p1Input, p2Input, p2AiEnabled);
        }
        // インゲームリプレイ再生（Task 183）：保存済みリプレイを入力として注入する。
        if (ingameReplay != null && ingameReplay.isReplaying()) {
            ingameReplay.applyReplayFrame(p1Input, p2Input);
        }
        // タイムアタックの残り時間を HUD に即時反映（毎フレーム再構築して秒表示を常に最新に保つ）。
        if (timeAttackMode) {
            controlsHint = buildControlsHint();
        }
        // インゲームリプレイ記録（Task 183）：通常バトル中（system-property 系は除く）の入力を蓄積する。
        if (!replay.isRecording() && !replay.isReplaying() && ingameReplay == null && !round.isFinished()) {
            int p1Mask = playerInputToMask(p1Input);
            int p2Mask = p2AiEnabled ? 0 : playerInputToMask(p2Input);
            ingameReplayBuffer.add(new int[]{p1Mask, p2Mask, p2AiEnabled ? 1 : 0});
        }
        update();
        // インゲームリプレイ自動保存（Task 183）：マッチ確定の初フレームに replays/last.rep へ書き出す。
        if (round.isFinished() && !ingameSaved && !replay.isRecording() && !replay.isReplaying()
                && ingameReplay == null && !ingameReplayBuffer.isEmpty()) {
            saveIngameReplay();
        }
        renderer.setSuperFlash(superFlashFrames); // スーパーフラッシュ演出（Task 169）：残りフレームをレンダラへ
        // 決着後の「タイトルへ戻る」ヒント表示は通常プレイのみ（撮影/リプレイは結果バナーを従来どおり凍結表示）。
        // 撮影は既定で非表示だが、証跡用に -x returntotitle=true で結果バナーに重ねて撮れる（後方互換）。
        boolean interactiveFinished = round.isFinished() && !screenshot.isEnabled()
                && !replay.isReplaying() && !replay.isRecording();
        renderer.setReturnToTitleHint(interactiveFinished || screenshot.returnToTitleHintForced());
        // リマッチヒント：通常プレイのマッチ確定後かつ直前マッチ情報が保存済みの場合に表示する（Task 178）。
        // 撮影は既定で非表示だが -x rematch=true で証跡用に重ねられる（後方互換）。
        renderer.setRematchAvailable((interactiveFinished && rematchP1Id != null) || screenshot.rematchHintForced());
        // アーケードスコア（Task 186）：サバイバル/タイムアタックがゲームオーバー（P1 敗北）になったらスコアを表示する。
        // P1 が勝利して次戦へ進む間は -1（非表示）。撮影用オーバーライド（-x arcadescore=N）でも強制表示できる。
        int shownScore = screenshot.arcadeScoreOverride();
        if (shownScore < 0 && interactiveFinished) {
            if (survivalMode && round.getMatchWinner() != com.phantomnexus.runtime.battle.RoundManager.Winner.P1) {
                shownScore = survivalKills * 1000;
            } else if (timeAttackMode && round.getMatchWinner() != com.phantomnexus.runtime.battle.RoundManager.Winner.P1) {
                shownScore = timeAttackKills * 1000;
            }
        }
        renderer.setArcadeScore(shownScore);
        // アーケードハイスコア登録（Task 187）：ゲームオーバー確定の最初のフレームに 1 回だけスコアを記録する。
        if (shownScore >= 0 && !arcadeScoreRecorded && screenshot.arcadeScoreOverride() < 0) {
            arcadeScoreRecorded = true;
            recordHighScore(shownScore);
        }
        // 撮影用ハイスコアオーバーライド（Task 187）：-x highscores=N1,N2 でランキングを強制表示する。
        java.util.List<Integer> hsOverride = screenshot.highScoresOverride();
        if (!hsOverride.isEmpty()) {
            renderer.setHighScores(hsOverride);
        }
        renderer.renderScene(fighter1, fighter2, animator1, animator2, projectiles, damagePopups, hitSparks,
                landingDusts, round, debugOverlay, controlsHint, statusLine(), p1Inputs, moveListVisible);
        // 描画後にフレームバッファを撮影（撮影モード時のみ。完了したら自動終了）。
        screenshot.maybeCapture();
    }

    /** 入力 → コマンド検出 → 攻撃・移動・ジャンプ → 押し合い解消 → ヒット判定 → 勝敗 → 向き直し → アニメ進行。 */
    private void update() {
        // ダメージ数値ポップアップは決着 / ラウンド間でも上昇・フェードを続けるため、凍結ガードより前に進める
        // （KO を決めた一撃の数字が止まらず最後まで浮かぶ）。純粋な演出で戦闘結果には影響しない。
        updateDamagePopups();
        // ヒットスパークも同様に凍結ガードより前で aging（KO を決めた一撃の火花が最後まで弾ける）。
        updateHitSparks();
        // 着地の砂煙も凍結ガードより前で aging（凍結中も土埃は広がり続ける。Task 131）。
        updateLandingDust();
        // マッチ決着後は全更新を凍結して結果表示の静止画を保つ。
        if (round.isFinished()) {
            return;
        }
        // ヒットストップ（Task 86）：命中直後の数フレーム、ファイター更新・判定・タイマー・アニメを凍結して
        // 衝撃を演出する（エフェクトの aging は上で済ませてあるので、火花/数字は止まらず動き続ける）。固定値＝決定的。
        if (hitstopFrames > 0) {
            hitstopFrames--;
            return;
        }
        // スーパーフラッシュ（Task 108）：スーパー必殺技の発動演出として両者・判定・タイマーを凍結する（ヒットストップの長尺版）。
        // エフェクト aging は上で済ませてあるので火花/数字は動き続ける。固定値＝決定的。
        if (superFlashFrames > 0) {
            superFlashFrames--;
            return;
        }
        // KO スローモーション（Task 115）：決着の一撃後のスロー再生中は、戦闘更新を KO_SLOW_FACTOR フレームに 1 回へ
        // 間引いて動きを遅くする（間のフレームは更新せず＝同じ絵を描き続ける＝スロー）。固定値＝決定的。
        if (koSlowFrames > 0) {
            koSlowFrames--;
            if (koSlowFrames % GameConstants.KO_SLOW_FACTOR != 0) {
                return;
            }
        }
        // ラウンド開始の "FIGHT!" バナーが初出のフレームに SE を 1 回だけ鳴らす。
        if (round.isFightFlash() && !fightSoundPlayed) {
            fightSoundPlayed = true;
            sounds.playRoundStart();
        }
        // ラウンド間インターバル中・ラウンド開始イントロ（"ROUND N"/"FIGHT!"）中は
        // ファイター操作・判定を停止し、カウントダウンのみ進める。
        if (!round.isBetweenRounds() && !round.isRoundIntro()) {
            updateFighterInput(fighter1, p1Input, history1, 1);
            if (p2AiEnabled) {
                p2Ai.control(fighter2, fighter1);
                // AI が飛び道具牽制（Task 64）でこのフレームに必殺技を発射していたら弾を生成する。
                // AI は updateFighterInput を通らないため、打撃必殺技（対空）と違い飛び道具は弾生成だけ Core が担う。
                Move aiProjectile = p2Ai.consumePendingProjectile();
                if (aiProjectile != null) {
                    spawnProjectile(fighter2, aiProjectile, false);
                }
                // AI がスーパー必殺技（Task 110）を発動していたら、メーター消費・スーパーフラッシュ凍結・
                // （飛び道具なら）弾生成を Core が行う（AI は updateFighterInput を通らないため・Task 108 の人間経路と対）。
                Move aiSuper = p2Ai.consumePendingSuper();
                if (aiSuper != null) {
                    fighter2.spendFullMeter();
                    superFlashFrames = GameConstants.SUPER_FLASH_FRAMES;
                    if (aiSuper.isProjectile()) {
                        spawnProjectile(fighter2, aiSuper, false);
                    }
                }
            } else {
                updateFighterInput(fighter2, p2Input, history2, 2);
            }
            if (commandTimer1 > 0) {
                commandTimer1--;
            }
            if (commandTimer2 > 0) {
                commandTimer2--;
            }
            // 着地の砂煙（Task 131）／踏み切りの砂煙（Task 135）：このフレームに滞空⇔接地へ遷移した
            // ファイターの足元に土埃を出す。物理（着地 / ジャンプ）は上の fighter.update 内で済んでいるので、
            // 被弾処理（resolveHit / updateProjectiles）より前に遷移を検出する＝着地と同フレームに
            // launch/groundBounce で再び滞空にされても砂煙を逃さない。純演出・乱数なし。
            detectLanding(fighter1, p1WasGrounded);
            detectLanding(fighter2, p2WasGrounded);
            detectTakeoff(fighter1, p1WasGrounded);
            detectTakeoff(fighter2, p2WasGrounded);
            p1WasGrounded = fighter1.isGrounded();
            p2WasGrounded = fighter2.isGrounded();
            // ダッシュ開始の砂煙（Task 140）：地上ダッシュ開始（false→true）の足元に土埃を出す。
            detectDashStart(fighter1, p1WasDashing);
            detectDashStart(fighter2, p2WasDashing);
            p1WasDashing = fighter1.isDashing();
            p2WasDashing = fighter2.isDashing();
            // 押し合い解消（pushbox の重なりを左右へ分離）。
            CollisionSystem.resolvePush(fighter1, fighter2);
            // ヒット判定（active hitbox × 相手 hurtbox）。多段ヒット防止のため攻撃ごと 1 回だけ確定する。
            resolveHit(fighter1, fighter2);
            resolveHit(fighter2, fighter1);
            // 飛び道具（必殺技）の更新と命中処理（Task 20）。
            updateProjectiles();
        }
        // タイムアタックモード（Task 185）：1 フレームごとに残り時間を減らす。タイムアップで P2 を即 KO して確定させる。
        if (timeAttackMode && timeAttackFramesLeft > 0) {
            timeAttackFramesLeft--;
            if (timeAttackFramesLeft <= 0) {
                // 時間切れ：P2 の HP を 0 にして KO＝P1 の "勝利" として処理するのではなく、
                // P1 の HP を 0 にしてタイムアップ GAME OVER 扱いにする（タイムアップは P1 の敗北）。
                fighter1.forceKO();
            }
        }
        // トレーニングモード（Task 90）：勝敗判定の前に両者の HP を満タンへ戻す＝無限 HP のダミーで KO せず練習できる。
        // ダメージ数値ポップアップ・コンボカウンターは被弾時に確定済みなので、コンボ練習の情報はそのまま見える。
        if (trainingMode) {
            fighter1.restoreFullHp();
            fighter2.restoreFullHp();
        }
        // KO スローモーション（Task 115）の開始判定：決着の一撃でどちらかが KO したら、まだスロー未開始なら開始する
        // （このラウンド 1 回）。スロー中は下の round.update を保留して即確定を防ぎ、スロー終了後に確定させる。
        if (koSlowFrames <= 0 && !koSlowTriggered && (fighter1.isKO() || fighter2.isKO())) {
            koSlowFrames = GameConstants.KO_SLOW_FRAMES;
            koSlowTriggered = true;
            sounds.playKO();
        }
        // 勝敗 / ラウンド間カウントダウンを進める（KO スロー再生中は保留＝スロー終了後に確定）。
        if (koSlowFrames <= 0) {
            round.update(fighter1, fighter2);
            // カウントダウン完了 → ファイターをスポーン位置にリセットして新ラウンド開始。
            if (round.consumeNextRoundReady()) {
                resetFighters();
            }
        }
        fighter1.faceTowards(fighter2);
        fighter2.faceTowards(fighter1);
        // 描画状態の更新（移動・向き確定後にファイター状態からアニメ状態を導出して 1 tick 進める）。
        animator1.update(fighter1);
        animator2.update(fighter2);
    }

    /** ラウンド間リセット：両ファイターをスポーン位置に戻し、入力履歴・弾・AI をクリアする。 */
    private void resetFighters() {
        fighter1.reset(spawnX1, true);
        fighter2.reset(spawnX2, false);
        history1.reset();
        history2.reset();
        lastCommand1 = Command.NONE;
        lastCommand2 = Command.NONE;
        commandTimer1 = 0;
        commandTimer2 = 0;
        projectiles.clear();
        damagePopups.clear();
        hitSparks.clear();
        landingDusts.clear(); // 着地の砂煙（Task 131）もラウンド間でクリア
        p1WasGrounded = true;  // リセット直後は両者接地＝次フレームで誤検出しないよう接地で初期化
        p2WasGrounded = true;
        p1WasDashing = false;  // リセット直後は両者非ダッシュ（Task 140）
        p2WasDashing = false;
        hitstopFrames = 0; // ヒットストップ（Task 86）もラウンド間でクリア
        superFlashFrames = 0; // スーパーフラッシュ（Task 108）もラウンド間でクリア
        koSlowFrames = 0; // KO スローモーション（Task 115）もラウンド間でクリア
        koSlowTriggered = false; // 次ラウンドで再び KO スローを使えるようにする（Task 115）
        fightSoundPlayed = false; // 新ラウンドでまた "FIGHT!" 音を鳴らせるようにリセット
        p1Inputs.clear(); // 入力表示ログ（Task 96）もラウンド間でクリア
        lastInputToken = "";
        p2Ai.reset();
    }

    /**
     * {@link PlayerInput} の押下状態を {@link com.phantomnexus.runtime.input.InputAction} ordinal ビットのマスクに畳む
     * （インゲームリプレイ記録用・Task 183。{@code ReplayController.toMask} と同じ計算）。
     */
    private static int playerInputToMask(PlayerInput in) {
        int mask = 0;
        for (com.phantomnexus.runtime.input.InputAction a : com.phantomnexus.runtime.input.InputAction.values()) {
            if (in.isDown(a)) {
                mask |= 1 << a.ordinal();
            }
        }
        return mask;
    }

    /**
     * インゲームリプレイバッファを {@code replays/last.rep} へ書き出す（Task 183）。
     * 書き出し成功/失敗に関わらず {@code ingameSaved=true} と {@code replayAvailable} を更新する。
     */
    private void saveIngameReplay() {
        ingameSaved = true;
        String path = ingameReplayPath();
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
                w.write("PHANTOM_REPLAY v1");
                w.newLine();
                for (int[] fr : ingameReplayBuffer) {
                    w.write(fr[0] + "," + fr[1] + "," + fr[2]);
                    w.newLine();
                }
            }
            replayAvailable = true;
        } catch (Exception e) {
            Gdx.app.error("Replay", "インゲームリプレイの保存に失敗: " + path, e);
        }
    }

    /** ダメージ数値ポップアップを 1 フレーム進め、寿命切れを取り除く（毎フレーム呼ぶ。純粋な演出）。 */
    private void updateDamagePopups() {
        for (Iterator<DamagePopup> it = damagePopups.iterator(); it.hasNext(); ) {
            DamagePopup p = it.next();
            p.update();
            if (p.isExpired()) {
                it.remove();
            }
        }
    }

    /** ヒットスパークを 1 フレーム進め、寿命切れを取り除く（毎フレーム呼ぶ。純粋な演出。Task 38）。 */
    private void updateHitSparks() {
        for (Iterator<HitSpark> it = hitSparks.iterator(); it.hasNext(); ) {
            HitSpark s = it.next();
            s.update();
            if (s.isExpired()) {
                it.remove();
            }
        }
    }

    /** 命中位置にヒットスパークを 1 件生成する（ガード成立時は {@link HitSpark.Kind#GUARD} で色分け。Task 38）。 */
    private void spawnHitSpark(boolean blocked, float centerX, float centerY) {
        hitSparks.add(new HitSpark(blocked ? HitSpark.Kind.GUARD : HitSpark.Kind.HIT,
                centerX, centerY, GameConstants.HIT_SPARK_FRAMES));
        // 画面の微振動（hit shake・Task 132）：接触の手応えを増す純描画演出。クリーンヒットは強め・ガードは弱め。
        // 火花と同じ接触の単一チョークポイントから呼ぶので、打撃 / 飛び道具 / 投げ / 投げ抜けすべてで揺れる。
        renderer.triggerShake(blocked ? GameConstants.GUARD_SHAKE_MAGNITUDE : GameConstants.HIT_SHAKE_MAGNITUDE);
        // SE は呼び出し元ごとに種別を選べるよう、ここには書かない（投げ成立・パリィ等は別音）。
    }

    /** 着地の砂煙を 1 フレーム進め、寿命切れを取り除く（毎フレーム呼ぶ。純粋な演出。Task 131）。 */
    private void updateLandingDust() {
        for (Iterator<LandingDust> it = landingDusts.iterator(); it.hasNext(); ) {
            LandingDust d = it.next();
            d.update();
            if (d.isExpired()) {
                it.remove();
            }
        }
    }

    /**
     * 着地（滞空→接地の遷移）を検出し、足元に砂煙を 1 件生成する（Task 131）。
     * 前フレームが滞空（{@code !wasGrounded}）で今フレームが接地なら着地とみなす。純演出・乱数なし。
     */
    private void detectLanding(Fighter f, boolean wasGrounded) {
        if (!wasGrounded && f.isGrounded()) {
            landingDusts.add(new LandingDust(f.getX(), f.getY(), GameConstants.LANDING_DUST_FRAMES));
        }
    }

    /**
     * 踏み切り（接地→滞空の遷移＝ジャンプ）を検出し、蹴り上げた足元（床）に砂煙を 1 件生成する（Task 135）。
     * 前フレームが接地（{@code wasGrounded}）で今フレームが滞空ならジャンプ踏み切りとみなす。着地（Task 131）と
     * 対称の演出で、砂煙の見た目（{@link LandingDust}）をそのまま流用する。踏み切り後の足元は床から離れて
     * いるので原点 Y は現在位置でなく {@link GameConstants#GROUND_Y}（蹴り上げた地点）に置く。純演出・乱数なし。
     */
    private void detectTakeoff(Fighter f, boolean wasGrounded) {
        if (wasGrounded && !f.isGrounded()) {
            landingDusts.add(new LandingDust(f.getX(), GameConstants.GROUND_Y, GameConstants.LANDING_DUST_FRAMES));
        }
    }

    /**
     * ダッシュ開始（非ダッシュ→ダッシュの遷移）を検出し、足元に砂煙を 1 件生成する（Task 140）。
     * 地上ダッシュ（前ステップ / バックステップ）開始時のみ（空中ダッシュは滞空なので地上の砂煙を出さない）。
     * 着地（Task 131）/ 踏み切り（Task 135）と同じ {@link LandingDust} を流用する純演出・乱数なし。
     */
    private void detectDashStart(Fighter f, boolean wasDashing) {
        if (!wasDashing && f.isDashing() && f.isGrounded()) {
            landingDusts.add(new LandingDust(f.getX(), GameConstants.GROUND_Y, GameConstants.LANDING_DUST_FRAMES));
        }
    }

    /**
     * 実際に減った HP 量（{@code dealt}）が正なら、命中位置にダメージ数値ポップアップを生成する。
     * 量は HP 計算式を複製せず「適用前後の HP 差」で求めるため、0 クランプ（残 HP より大きいダメージ）も
     * 正確に表示できる。ガード成立時は {@link DamagePopup.Kind#CHIP} で色分けする。
     */
    private void spawnDamagePopup(int dealt, boolean blocked, float centerX, float centerY) {
        if (dealt <= 0) {
            return;
        }
        damagePopups.add(new DamagePopup(dealt, blocked ? DamagePopup.Kind.CHIP : DamagePopup.Kind.HIT,
                centerX, centerY, GameConstants.DAMAGE_POPUP_FRAMES));
    }

    /**
     * 1 プレイヤー分の入力を 1 回だけ読み取り（強制エッジの二重消費を避ける）、入力履歴へ記録し、
     * コマンド検出を行ってから {@link Fighter#update} へ渡す（Task 19/24）。
     * 弱/中/強の 3 ボタンを読み取り、コマンド技成立時はコマンド対応の必殺技を優先発動する。
     */
    private void updateFighterInput(Fighter f, PlayerInput in, InputHistory history, int player) {
        int dir = moveDir(in);
        boolean jump = in.isPressed(InputAction.UP);
        boolean lightPressed = in.isPressed(InputAction.ATTACK_LIGHT);
        boolean mediumPressed = in.isPressed(InputAction.ATTACK_MEDIUM);
        boolean heavyPressed = in.isPressed(InputAction.ATTACK_HEAVY);
        boolean anyAttack = lightPressed || mediumPressed || heavyPressed;
        // 押されたボタン（複数同時は軽い方が優先）
        AttackButton attackButton = lightPressed ? AttackButton.LIGHT
                : mediumPressed ? AttackButton.MEDIUM
                : heavyPressed ? AttackButton.HEAVY
                : null;
        // 向き相対のテンキー方向 + 攻撃立ち上がり（いずれかのボタン）を履歴に記録。
        int numpad = InputHistory.numpad(
                in.isDown(InputAction.LEFT), in.isDown(InputAction.RIGHT),
                in.isDown(InputAction.UP), in.isDown(InputAction.DOWN), f.isFacingRight());
        history.record(numpad, anyAttack);
        Command cmd = CommandDetector.detect(history);
        if (cmd != Command.NONE) {
            if (player == 2) {
                lastCommand2 = cmd;
                commandTimer2 = COMMAND_DISPLAY_FRAMES;
            } else {
                lastCommand1 = cmd;
                commandTimer1 = COMMAND_DISPLAY_FRAMES;
            }
        }
        boolean crouchHeld = in.isDown(InputAction.DOWN);
        // 投げ（Task 35）：地上・立ち（非しゃがみ）で投げボタンが押され、キャラに投げ技があれば最優先で発動する。
        // ガード不能の近接掴み。Fighter 側が予約語 "throw" を受けて専用経路で起動する（通常攻撃 / 必殺技は抑止）。
        boolean throwPressed = in.isPressed(InputAction.THROW);
        // 入力表示 HUD（Task 96）：P1 のテンキー方向（向き相対・6=前/2=下 等）＋押したボタンを 1 トークンにし、変化時のみ
        // ログへ積む（FG 定番の入力表示。トレーニング / 観戦の確認用）。throwPressed は上で読んだ値を流用（二重消費を避ける）。
        if (player == 1) {
            StringBuilder tok = new StringBuilder().append(numpad);
            if (lightPressed) tok.append("L");
            if (mediumPressed) tok.append("M");
            if (heavyPressed) tok.append("H");
            if (throwPressed) tok.append("T");
            String token = tok.toString();
            if (!token.equals(lastInputToken)) {
                p1Inputs.add(token);
                if (p1Inputs.size() > INPUT_LOG_MAX) {
                    p1Inputs.remove(0);
                }
                lastInputToken = token;
            }
        }
        // 投げボタンを押した接地フレームは投げ抜け猶予窓をアームする（掴まれた瞬間に抜けられる・Task 36）。
        // 自分の投げが成立しない間合い／状況でも、防御反応としての投げ抜け入力はここで受け付ける。
        if (throwPressed && f.isGrounded()) {
            f.armThrowTech();
        }
        // 地上では地上投げ（接地・非しゃがみ・throwMove 所持）、滞空中では空中投げ（Task 70・airThrowMove 所持）。
        boolean groundThrow = f.isGrounded() && !crouchHeld && f.getDef().getThrowMove() != null;
        boolean airThrow = !f.isGrounded() && f.getDef().getAirThrowMove() != null;
        boolean throwReq = throwPressed && (groundThrow || airThrow);
        if (throwReq) {
            // 投げ要求時は通常攻撃を抑止（発動は throwReq として Fighter.update へ渡す）。
            attackButton = null;
        } else if (handleCommandInput(f, cmd, anyAttack)) {
            attackButton = null;
        }
        f.update(dir, jump, attackButton, crouchHeld, throwReq);
    }

    /**
     * コマンド入力に対応する必殺技（スーパー / EX / 通常特殊）を発動する。
     * 発動成功時は true を返し、呼び出し元が attackButton を null にして通常攻撃を抑止する。
     */
    private boolean handleCommandInput(Fighter f, Command cmd, boolean anyAttack) {
        if (cmd == Command.SUPER && anyAttack) {
            // スーパー必殺技（Task 108）：236236＋攻撃。super 技を所持しメーター満タンなら消費して発動＋スーパーフラッシュ凍結。
            // 条件を満たさなければ波動拳（HADOUKEN）にフォールバック（236236 は 236 を内包するため・満タンなら EX 波動拳）。
            Move superMove = findSpecialMove(f.getDef(), Command.SUPER);
            if (superMove != null && superMove.isSuper() && f.hasFullMeter() && f.startSpecial(superMove, false)) {
                f.spendFullMeter();
                superFlashFrames = GameConstants.SUPER_FLASH_FRAMES;
                if (superMove.isProjectile()) {
                    spawnProjectile(f, superMove, false);
                }
                return true;
            }
            // フォールバック：HADOUKEN（EX 対応）
            Move hado = findSpecialMove(f.getDef(), Command.HADOUKEN);
            boolean ex = hado != null && f.hasFullMeter();
            if (hado != null && f.startSpecial(hado, ex)) {
                if (ex) {
                    f.spendFullMeter();
                }
                if (hado.isProjectile()) {
                    spawnProjectile(f, hado, ex);
                }
                return true;
            }
            return false;
        } else if (cmd != Command.NONE && anyAttack) {
            // 必殺技（Task 20/24）：コマンド成立かつ攻撃ボタンありなら対応する必殺技を発動。
            Move special = findSpecialMove(f.getDef(), cmd);
            boolean ex = special != null && f.hasFullMeter();
            if (special != null && f.startSpecial(special, ex)) {
                if (ex) {
                    f.spendFullMeter();
                }
                if (special.isProjectile()) {
                    spawnProjectile(f, special, ex);
                }
                return true;
            }
        }
        return false;
    }

    /** キャラの specialMoves[] からコマンドに対応する技を返す（見つからなければ null）。 */
    private static Move findSpecialMove(Character def, Command cmd) {
        Move[] specials = def.getSpecialMoves();
        if (specials == null) {
            return null;
        }
        String cmdName = cmd.name(); // "HADOUKEN" / "CHARGE_SHOT" / "DOWN_ATTACK"
        for (Move m : specials) {
            String mc = m.getCommand();
            if (mc != null && cmdName.equalsIgnoreCase(mc.trim())) {
                return m;
            }
        }
        return null;
    }

    /**
     * 必殺技（飛び道具）の弾を発射者の前方に生成する（Task 20/24）。
     * {@code ex} 指定時はメーター消費の EX 版＝ダメージ {@link GameConstants#EX_DAMAGE_MULTIPLIER} 倍・
     * 判定/描画 {@link GameConstants#EX_PROJECTILE_SCALE} 倍の大型弾になる（Task 44）。
     */
    private void spawnProjectile(Fighter f, Move move, boolean ex) {
        if (move == null || !move.isProjectile()) {
            return;
        }
        Character d = f.getDef();
        float scale = ex ? GameConstants.EX_PROJECTILE_SCALE : 1f;
        float width = move.getHitboxWidth() * scale;
        float height = move.getHitboxHeight() * scale;
        int damage = ex
                ? Math.round(move.getDamage() * GameConstants.EX_DAMAGE_MULTIPLIER)
                : move.getDamage();
        float front = f.isFacingRight() ? f.getX() + d.getWidth() / 2f : f.getX() - d.getWidth() / 2f;
        float spawnX = f.isFacingRight()
                ? front + move.getHitboxOffsetX() + width / 2f
                : front - move.getHitboxOffsetX() - width / 2f;
        float spawnY = f.getY() + move.getHitboxOffsetY();
        float vx = (f.isFacingRight() ? 1f : -1f) * move.getProjectileSpeed();
        projectiles.add(new Projectile(spawnX, spawnY, vx, width, height, damage, f, ex));
        sounds.playSpecial();
        // 発射のマズルフラッシュ（Task 144）：弾の生成位置に火花を 1 つ出して発射の手応えを足す（純演出）。
        // 発射は「接触」ではないので画面振動（spawnHitSpark が伴う hit shake）は誘発せず、火花だけを直接追加する。
        hitSparks.add(new HitSpark(HitSpark.Kind.HIT, spawnX, spawnY + height / 2f, GameConstants.HIT_SPARK_FRAMES));
    }

    /** 飛び道具を 1 フレーム進め、相手命中で被弾適用、命中 / 画面外で消滅させる（Task 20）。 */
    private void updateProjectiles() {
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            p.update();
            Fighter target = p.getOwner() == fighter1 ? fighter2 : fighter1;
            if (p.isAlive() && CollisionSystem.hits(p, target)) {
                int knockbackDir = target.getX() >= p.getX() ? 1 : -1;
                boolean blocked = target.isGuarding();
                int before = target.getCurrentHp();
                if (blocked) {
                    target.applyGuard(p.getDamage(), knockbackDir);
                } else {
                    target.applyHit(p.getDamage(), GameConstants.HITSTUN_FRAMES, knockbackDir);
                    target.addStun(p.getDamage()); // めまい蓄積（Task 79・飛び道具ヒットも蓄積）
                }
                float sparkY = target.getY() + target.getDef().getHeight() / 2f;
                spawnDamagePopup(before - target.getCurrentHp(), blocked, p.getX(), sparkY);
                if (blocked) sounds.playGuard(); else sounds.playHitHeavy();
                spawnHitSpark(blocked, p.getX(), sparkY);
                awardMeter(p.getOwner(), target, blocked);
                triggerHitstop(blocked); // ヒットストップ（Task 86）
                p.kill();
            }
            if (!p.isAlive()) {
                it.remove();
            }
        }
    }

    /** attacker の active hitbox が defender に当たり、まだ未命中ならダメージ・のけぞりを適用する（Task 13 / Task 27 / Task 31 / Task 35）。 */
    private void resolveHit(Fighter attacker, Fighter defender) {
        // 多段ヒット（Task 74）：単発技は 1 回、多段技は hits 回まで（hitGap 間隔で）ヒットを確定する。
        // canHitNow() が残りヒット数と間隔を見るため、従来の「命中済みなら return」を一般化したもの。
        if (!attacker.canHitNow() || !CollisionSystem.isHitting(attacker, defender)) {
            return;
        }
        // 投げは種別に応じて掴める相手の接地状態が決まる（Task 35 / 空中投げ Task 70）。
        //   地上投げは地上の相手のみ／空中投げ（airThrowMove）は空中の相手のみ掴める。
        // 種別は「発動した Move」で固定（Fighter.isAirThrowing）＝低空空中投げが着地しても判定がブレない（Codex 指摘）。
        //   空中投げ → 相手が接地なら whiff、地上投げ → 相手が滞空なら whiff（isAirThrowing == defender.isGrounded）。
        // 不一致なら grab box が重なった時点で whiff として消費し（markAttackConnected）、同じ active 区間内に
        // 相手の接地状態が変わっても掴み直さない＝地上投げはジャンプで、空中投げは着地で確実に回避できる。
        if (attacker.isThrowing() && attacker.isAirThrowing() == defender.isGrounded()) {
            attacker.markAttackConnected();
            return;
        }
        attacker.markAttackConnected();
        Hitbox hb = CollisionSystem.activeHitbox(attacker);
        int knockbackDir = defender.getX() >= attacker.getX() ? 1 : -1;
        int before = defender.getCurrentHp();
        // 投げはガード不能だが、被掴み側が直近に投げボタンを押していれば投げ抜け（Task 36）。
        // 投げ抜けは地上投げ限定（空中投げ＝committal な対空択で抜けられない・Task 70）。種別は発動した Move で固定
        // （isAirThrowing）するので、接地直後にジャンプした相手の残存 tech 窓で空中投げが抜かれる境界も塞がる。
        if (attacker.isThrowing()) {
            // 投げ抜け不能（command throw・Task 94）の投げは tech 窓を無視して必ず掴む（地上投げの抜け判定をスキップ）。
            Move throwMove = attacker.getCurrentMove();
            boolean noTech = throwMove != null && throwMove.isNoTech();
            if (!noTech && !attacker.isAirThrowing() && defender.canTechThrow()) {
                // 投げ抜け成立：両者をノーダメージで反対方向へ弾き、短い硬直に入れる（ガード不能投げ唯一の対抗策）。
                defender.applyThrowTech(knockbackDir);
                attacker.applyThrowTech(-knockbackDir);
                // 掴みが弾かれた接触点に火花（ノーダメージなのでポップアップは出さない）。
                spawnHitSpark(false, hb.getX() + hb.getWidth() / 2f, hb.getY() + hb.getHeight() / 2f);
                triggerHitstop(false); // ヒットストップ（Task 86・投げ抜けの弾き合い）
                return;
            }
            // 投げ成立：ガード中でもフルダメージ＋長い hitstun を適用する（Task 35）。
            defender.applyThrow(hb.getDamage(), knockbackDir);
            sounds.playThrow();
            spawnDamagePopup(before - defender.getCurrentHp(), false,
                    hb.getX() + hb.getWidth() / 2f, hb.getY() + hb.getHeight() / 2f);
            spawnHitSpark(false, hb.getX() + hb.getWidth() / 2f, hb.getY() + hb.getHeight() / 2f);
            // 投げ成立はフルダメージの攻撃なので、通常ヒットと同様にメーターを貯める（ダメージ＝メーターの一貫性）。
            // 非加算はノーダメージの outcome のみ（投げ抜け＝上の return / 空中 whiff＝markAttackConnected で先に return）。
            awardMeter(attacker, defender, false);
            triggerHitstop(false); // ヒットストップ（Task 86・投げ成立）
            return;
        }
        // パリィ（Task 105）：被攻撃側が前方タップの受付窓内なら、打撃をダメージ・chip・のけぞりなしで完全に弾く。
        // 攻撃側の硬直を反撃確定にできる committal な防御テク（投げは上で処理済み＝パリィ対象外・markAttackConnected は上で済み）。
        if (defender.canParry()) {
            defender.applyParry();
            // いなしの火花（ノーダメージなのでダメージ数値ポップアップは出さない）。
            sounds.playGuard();
            spawnHitSpark(true, hb.getX() + hb.getWidth() / 2f, hb.getY() + hb.getHeight() / 2f);
            triggerHitstop(true); // 軽いヒットストップ（いなし感・ガード相当）
            return;
        }
        // ガード高さ属性（Task 33）で成立可否を決める：
        //   low（下段／しゃがみ通常技。Task 31）   → しゃがみガードのみ成立（立ちガード貫通）
        //   overhead（上段）                        → 立ちガードのみ成立（しゃがみガード貫通）
        //   mid（中段／既定。Task 27/30）           → 立ち / しゃがみどちらでも成立
        boolean blocked = false;
        if (defender.isGuarding()) {
            switch (effectiveAttackHeight(attacker)) {
                case LOW:
                    blocked = defender.isCrouchGuarding();
                    break;
                case OVERHEAD:
                    blocked = !defender.isCrouchGuarding();
                    break;
                default: // MID
                    blocked = true;
                    break;
            }
        }
        // スーパーアーマー（Task 80）：相手が startup のアーマー技中なら、のけぞらせずダメージのみ与えて技を継続させる。
        // ガード成立時は対象外（防御が優先）。投げは上で処理済みのためここには来ない＝アーマーは打撃のみ吸収する。
        boolean armored = !blocked && defender.isArmorActive();
        // カウンターヒット（Task 71）：相手の攻撃 startup 中（技を出しきる前）に打撃を当てたら差し返し成立。
        // ガード成立・アーマー吸収時は対象外（防御を崩した / のけぞらせたわけではない）。乱数なし＝決定的。
        boolean counter = !blocked && !armored
                && defender.getAttackPhase() == com.phantomnexus.runtime.battle.AttackPhase.STARTUP;
        int dealtDamage = counter
                ? Math.max(1, Math.round(hb.getDamage() * GameConstants.COUNTER_HIT_DAMAGE_SCALE))
                : hb.getDamage();
        applyHitDamage(attacker, defender, hb, knockbackDir, blocked, armored, counter, dealtDamage, before);
    }

    /**
     * ヒット解決の最終段階：ダメージ適用・スタン蓄積・エフェクト生成をまとめて行う。
     * resolveHit() の投げ・パリィ処理後に呼ばれる通常打撃ヒットの共通処理。
     */
    private void applyHitDamage(Fighter attacker, Fighter defender, Hitbox hb, int knockbackDir,
                                 boolean blocked, boolean armored, boolean counter, int dealtDamage,
                                 int hpBefore) {
        if (blocked) {
            // ガード成立：chip ダメージのみ（のけぞりなし）。
            defender.applyGuard(hb.getDamage(), knockbackDir);
            // プッシュブロック（Task 111）：攻撃側も相手と反対方向（-knockbackDir）へ押し戻して間合いを作る（固め対策）。
            attacker.applyGuardPushback(-knockbackDir);
        } else if (armored) {
            // アーマー吸収：のけぞらず damage のみ受けて技を継続（Task 80）。
            defender.absorbArmorHit(dealtDamage, knockbackDir);
        } else if (attacker.getCurrentMove() != null && attacker.getCurrentMove().isKnockdown()) {
            // ダウン技（Task 60）
            defender.applyKnockdown(dealtDamage, knockbackDir, attacker.getCurrentMove().isHardKnockdown());
        } else {
            int hitstun = GameConstants.HITSTUN_FRAMES + (counter ? GameConstants.COUNTER_HIT_BONUS_HITSTUN : 0);
            boolean wallBounce = attacker.getCurrentMove() != null && attacker.getCurrentMove().isWallBounce();
            boolean groundBounce = attacker.getCurrentMove() != null && attacker.getCurrentMove().isGroundBounce();
            float launch = attacker.getCurrentMove() != null ? attacker.getCurrentMove().getLaunch() : 0f;
            if (wallBounce) {
                defender.applyWallBounce(dealtDamage, hitstun, knockbackDir);
            } else if (groundBounce) {
                defender.applyGroundBounce(dealtDamage, hitstun, knockbackDir);
            } else if (launch > 0f) {
                defender.applyLaunch(dealtDamage, hitstun, knockbackDir, launch);
            } else {
                defender.applyHit(dealtDamage, hitstun, knockbackDir);
            }
        }
        if (!blocked && !armored) {
            defender.addStun(dealtDamage);
        }
        if (counter) {
            defender.markCounterHit();
        }
        float sparkX = hb.getX() + hb.getWidth() / 2f;
        float sparkY = hb.getY() + hb.getHeight() / 2f;
        spawnDamagePopup(hpBefore - defender.getCurrentHp(), blocked, sparkX, sparkY);
        if (blocked) {
            sounds.playGuard();
        } else {
            Move cm = attacker.getCurrentMove();
            AttackButton btn = cm != null ? cm.getButton() : null;
            if (btn == AttackButton.HEAVY) sounds.playHitHeavy();
            else if (btn == AttackButton.MEDIUM) sounds.playHitMedium();
            else sounds.playHitLight();
        }
        spawnHitSpark(blocked, sparkX, sparkY);
        awardMeter(attacker, defender, blocked);
        triggerHitstop(blocked);
    }

    /**
     * 攻撃の決着で攻防両者の必殺技ゲージを貯める（Task 44）。命中は攻撃側が多く・防御側が少なく、
     * ガードは両者わずかに貯まる。固定値のみで乱数なし（入力リプレイの決定性を保つ）。
     */
    /** ヒットストップ（Task 86）を発生させる（命中=長め / ガード=短め）。既存値より長い場合のみ更新する。 */
    private void triggerHitstop(boolean blocked) {
        int frames = blocked ? GameConstants.HITSTOP_BLOCK_FRAMES : GameConstants.HITSTOP_FRAMES;
        if (frames > hitstopFrames) {
            hitstopFrames = frames;
        }
    }

    private static void awardMeter(Fighter attacker, Fighter defender, boolean blocked) {
        if (blocked) {
            attacker.gainMeter(GameConstants.METER_GAIN_ON_GUARD);
            defender.gainMeter(GameConstants.METER_GAIN_ON_GUARD);
        } else {
            attacker.gainMeter(GameConstants.METER_GAIN_ON_HIT);
            defender.gainMeter(GameConstants.METER_GAIN_ON_TAKE);
        }
    }

    /**
     * 攻撃の実効ガード高さ（Task 33）。しゃがみ中に出した通常技は脚部 hitbox の下段（{@link GuardHeight#LOW}）
     * として扱い（Task 31）、それ以外は技定義の {@link Move#getGuardHeight()} に従う。技未定義時は中段（既定）。
     */
    private static GuardHeight effectiveAttackHeight(Fighter attacker) {
        if (attacker.isCrouchAttacking()) {
            return GuardHeight.LOW;
        }
        Move m = attacker.getCurrentMove();
        return m != null ? m.getGuardHeight() : GuardHeight.DEFAULT;
    }

    /** 左右入力を移動方向（-1 / 0 / +1）に変換する。 */
    private static int moveDir(PlayerInput input) {
        int dir = 0;
        if (input.isDown(InputAction.RIGHT)) {
            dir += 1;
        }
        if (input.isDown(InputAction.LEFT)) {
            dir -= 1;
        }
        return dir;
    }

    /** 各ファイターの座標・向き・接地状態（＋検出コマンド）を 1 行で返す（動作確認用 HUD）。 */
    /** 現在の AI 難易度ラベル（小文字・HUD 表示用・Task 56）。 */
    private String aiDifficultyLabel() {
        return p2Ai.getDifficulty().name().toLowerCase();
    }

    /** 操作ガイド HUD 文字列を組み立てる（難易度ラベルを含むため F3 切替時にも再構築する・Task 78）。 */
    private String buildControlsHint() {
        String survival = survivalMode ? "  [SURVIVAL] kills=" + survivalKills : "";
        String timeAttack = timeAttackMode
                ? "  [TIME ATTACK] " + (timeAttackFramesLeft / 60) + "s kills=" + timeAttackKills : "";
        return "P1 " + p1Input.describe()
                + "   [F1] hitboxes  [F2] P2 AI(" + aiDifficultyLabel() + ")  [F3] difficulty"
                + "  [F4] training(" + (trainingMode ? "on" : "off") + ")  [F5] moves(" + (moveListVisible ? "on" : "off") + ")"
                + "  [M] SE(" + (sounds.isEnabled() ? "on" : "off") + ")"
                + survival
                + timeAttack;
    }

    private String statusLine() {
        return String.format(
                "%s x=%.0f y=%.0f %s%s%s    %s x=%.0f y=%.0f %s%s%s",
                fighter1.getDef().getName(), fighter1.getX(), fighter1.getY(), facingArrow(fighter1), airTag(fighter1),
                commandTag(1),
                fighter2.getDef().getName(), fighter2.getX(), fighter2.getY(), facingArrow(fighter2), airTag(fighter2),
                commandTag(2));
    }

    /** 直近に検出したコマンドを表示窓内のあいだだけ付記する（無ければ空文字）。 */
    private String commandTag(int player) {
        if (player == 2) {
            return commandTimer2 > 0 ? "  <" + lastCommand2.label() + ">" : "";
        }
        return commandTimer1 > 0 ? "  <" + lastCommand1.label() + ">" : "";
    }

    private static String airTag(Fighter f) {
        return f.isGrounded() ? "" : " (air)";
    }

    private static String facingArrow(Fighter f) {
        return f.isFacingRight() ? ">" : "<";
    }

    @Override
    public void resize(int width, int height) {
        renderer.resize(width, height);
    }

    @Override
    public void dispose() {
        if (replay != null) {
            replay.close();
        }
        renderer.dispose();
        sounds.dispose();
    }
}
