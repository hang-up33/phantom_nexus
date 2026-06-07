package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.runtime.battle.RoundManager;
import com.phantomnexus.runtime.debug.DebugOverlay;
import com.phantomnexus.runtime.debug.ScreenshotController;
import com.phantomnexus.runtime.input.Command;
import com.phantomnexus.runtime.input.CommandDetector;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.InputHistory;
import com.phantomnexus.runtime.input.PlayerInput;
import com.phantomnexus.runtime.rendering.FighterAnimator;
import com.phantomnexus.runtime.rendering.GameRenderer;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.schema.CharacterLoader;
import com.phantomnexus.shared.schema.StageLoader;
import com.phantomnexus.shared.types.BattleRules;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Stage;

/**
 * Phantom Nexus アプリケーション本体（ゲームループ / ライフサイクル）。
 *
 * <p>Core はライフサイクルと毎フレームの更新順序（入力 → 更新 → 描画）の制御に専念する。
 * 2 体の {@link Fighter} を保持し、{@link PlayerInput} の左右入力で移動・ジャンプ入力で跳躍させる
 * （P1=WASD / P2=方向キー、ジャンプは Task 8）。更新後に互いへ向き直し、{@link GameRenderer} へ描画委譲する。
 * サンプルキャラ定義はコード生成の暫定で、Task 16 で JSON 読込供給に差し替える。
 */
public class PhantomNexusGame extends ApplicationAdapter {

    private GameRenderer renderer;
    private PlayerInput p1Input;
    private PlayerInput p2Input;
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
    private String controlsHint;
    private ScreenshotController screenshot;

    /** 検出コマンドを HUD に表示し続けるフレーム数。 */
    private static final int COMMAND_DISPLAY_FRAMES = 90;

    @Override
    public void create() {
        renderer = new GameRenderer();
        // ステージを外部 JSON から読み込み、背景描画に設定する（Task 17）。
        Stage stage = StageLoader.load("stage001");
        renderer.setStage(stage);
        // ヘッドレス自動スクショ（phantom.screenshot.* 指定時のみ有効。通常起動には無影響）。
        screenshot = new ScreenshotController();
        p1Input = PlayerInput.player1Defaults();
        p2Input = PlayerInput.player2Defaults();
        // 過渡状態の撮影用に、指定があれば起動時から入力を押下状態に固定する（通常は空＝無影響）。
        p1Input.setForcedHold(screenshot.heldActions(1));
        p2Input.setForcedHold(screenshot.heldActions(2));
        // 外部 JSON からキャラクター定義を読み込む（Task 16）。データ I/O は Shared/Schema が単一の真実。
        Character aoi = CharacterLoader.load("fighter001");
        Character akane = CharacterLoader.load("fighter002");
        // 撮影モード時は初期 X をオーバーライド可能（近接が必要な被弾スクショ等の再現用）。
        fighter1 = new Fighter(aoi, screenshot.spawnX(1, GameConstants.P1_SPAWN_X), true);
        fighter2 = new Fighter(akane, screenshot.spawnX(2, GameConstants.P2_SPAWN_X), false);
        // アニメーション状態機械（Task 9）。各ファイターの実行時状態から idle/walk/jump を導出する。
        animator1 = new FighterAnimator();
        animator2 = new FighterAnimator();
        // 対戦ルール / ラウンド管理（Task 14）。撮影時は制限時間をオーバーライド可能（結果表示の撮影用）。
        BattleRules rules = new BattleRules(screenshot.timeLimitSeconds(BattleRules.defaults().getTimeLimitSeconds()), 1);
        round = new RoundManager(rules);
        // デバッグ当たり判定表示（Task 18）。既定 OFF・F1 でトグル。撮影時は debug=true で強制 ON。
        debugOverlay = new DebugOverlay();
        debugOverlay.setEnabled(screenshot.debugEnabled());
        controlsHint = "P1 " + p1Input.describe() + "      P2 Arrows + RCtrl   [F1] hitboxes";
    }

    @Override
    public void render() {
        // 撮影用タイムド入力スクリプト（コマンド技の再現）。毎フレーム先頭で押下を更新する。
        screenshot.applyTimedHolds(p1Input, p2Input);
        // デバッグ表示のトグル（グローバルキー。プレイヤー入力とは別系統のため Gdx を直接参照）。
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debugOverlay.toggle();
        }
        update();
        renderer.renderScene(fighter1, fighter2, animator1, animator2, round, debugOverlay,
                controlsHint, statusLine());
        // 描画後にフレームバッファを撮影（撮影モード時のみ。完了したら自動終了）。
        screenshot.maybeCapture();
    }

    /** 入力 → コマンド検出 → 攻撃・移動・ジャンプ → 押し合い解消 → ヒット判定 → 勝敗 → 向き直し → アニメ進行。 */
    private void update() {
        // ラウンド決着後は全更新を凍結して結果表示の静止画を保つ（MVP）。
        if (round.isFinished()) {
            return;
        }
        updateFighterInput(fighter1, p1Input, history1, 1);
        updateFighterInput(fighter2, p2Input, history2, 2);
        if (commandTimer1 > 0) {
            commandTimer1--;
        }
        if (commandTimer2 > 0) {
            commandTimer2--;
        }
        // 押し合い解消（pushbox の重なりを左右へ分離）。
        CollisionSystem.resolvePush(fighter1, fighter2);
        // ヒット判定（active hitbox × 相手 hurtbox）。多段ヒット防止のため攻撃ごと 1 回だけ確定する。
        resolveHit(fighter1, fighter2);
        resolveHit(fighter2, fighter1);
        // 勝敗判定（KO / タイムアップ）。決着したら次フレーム以降は凍結される。
        round.update(fighter1, fighter2);
        fighter1.faceTowards(fighter2);
        fighter2.faceTowards(fighter1);
        // 描画状態の更新（移動・向き確定後にファイター状態からアニメ状態を導出して 1 tick 進める）。
        animator1.update(fighter1);
        animator2.update(fighter2);
    }

    /**
     * 1 プレイヤー分の入力を 1 回だけ読み取り（強制エッジの二重消費を避ける）、入力履歴へ記録し、
     * コマンド検出を行ってから {@link Fighter#update} へ渡す（Task 19）。
     */
    private void updateFighterInput(Fighter f, PlayerInput in, InputHistory history, int player) {
        int dir = moveDir(in);
        boolean jump = in.isPressed(InputAction.UP);
        boolean attack = in.isPressed(InputAction.ATTACK);
        // 向き相対のテンキー方向 + 攻撃立ち上がりを履歴に記録（攻撃値は上で 1 回読んだものを再利用）。
        int numpad = InputHistory.numpad(
                in.isDown(InputAction.LEFT), in.isDown(InputAction.RIGHT),
                in.isDown(InputAction.UP), in.isDown(InputAction.DOWN), f.isFacingRight());
        history.record(numpad, attack);
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
        f.update(dir, jump, attack);
    }

    /** attacker の active hitbox が defender に当たり、まだ未命中ならダメージ・のけぞりを適用する（Task 13）。 */
    private static void resolveHit(Fighter attacker, Fighter defender) {
        if (!attacker.hasAttackConnected() && CollisionSystem.isHitting(attacker, defender)) {
            attacker.markAttackConnected();
            Hitbox hb = CollisionSystem.activeHitbox(attacker);
            // 後方への向き：defender が attacker より右なら +1（右へ吹き飛ぶ）。
            int knockbackDir = defender.getX() >= attacker.getX() ? 1 : -1;
            defender.applyHit(hb.getDamage(), GameConstants.HITSTUN_FRAMES, knockbackDir);
        }
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
        renderer.dispose();
    }
}
