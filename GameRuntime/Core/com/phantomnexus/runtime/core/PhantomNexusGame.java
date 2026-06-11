package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.phantomnexus.runtime.battle.AiController;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.DamagePopup;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.runtime.battle.Projectile;
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
import com.phantomnexus.shared.types.GuardHeight;
import com.phantomnexus.shared.types.Hitbox;
import com.phantomnexus.shared.types.Move;
import com.phantomnexus.shared.types.Stage;

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
    private final List<Projectile> projectiles = new ArrayList<>();
    // ダメージ数値ポップアップ（被弾 / ガード時の与ダメージ量を命中位置に浮かび上がらせる演出）。
    private final List<DamagePopup> damagePopups = new ArrayList<>();
    private final AiController p2Ai = new AiController();
    private boolean p2AiEnabled = true; // P2 を AI 制御にするか（F2 でトグル。Task 21）
    private String controlsHint;
    private ScreenshotController screenshot;
    private float spawnX1;
    private float spawnX2;

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
        spawnX1 = screenshot.spawnX(1, GameConstants.P1_SPAWN_X);
        spawnX2 = screenshot.spawnX(2, GameConstants.P2_SPAWN_X);
        fighter1 = new Fighter(aoi, spawnX1, true);
        fighter2 = new Fighter(akane, spawnX2, false);
        // アニメーション状態機械（Task 9）。各ファイターの実行時状態から idle/walk/jump を導出する。
        animator1 = new FighterAnimator();
        animator2 = new FighterAnimator();
        // 対戦ルール / ラウンド管理（Task 14 / Task 26）。撮影時は制限時間をオーバーライド可能（結果表示の撮影用）。
        BattleRules rules = new BattleRules(
                screenshot.timeLimitSeconds(BattleRules.defaults().getTimeLimitSeconds()),
                BattleRules.defaults().getRoundsToWin());
        round = new RoundManager(rules);
        // デバッグ当たり判定表示（Task 18）。既定 OFF・F1 でトグル。撮影時は debug=true で強制 ON。
        debugOverlay = new DebugOverlay();
        debugOverlay.setEnabled(screenshot.debugEnabled());
        // P2 の AI（Task 21）。既定 ON・F2 でトグル。撮影時は ai=false で人間（静止）に切替可能。
        p2AiEnabled = screenshot.aiEnabled(true);
        controlsHint = "P1 " + p1Input.describe() + "   [F1] hitboxes  [F2] P2 AI";
    }

    @Override
    public void render() {
        // 撮影用タイムド入力スクリプト（コマンド技の再現）。毎フレーム先頭で押下を更新する。
        screenshot.applyTimedHolds(p1Input, p2Input);
        // デバッグ表示 / AI のトグル（グローバルキー。プレイヤー入力とは別系統のため Gdx を直接参照）。
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debugOverlay.toggle();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            p2AiEnabled = !p2AiEnabled;
        }
        update();
        renderer.renderScene(fighter1, fighter2, animator1, animator2, projectiles, damagePopups, round, debugOverlay,
                controlsHint, statusLine());
        // 描画後にフレームバッファを撮影（撮影モード時のみ。完了したら自動終了）。
        screenshot.maybeCapture();
    }

    /** 入力 → コマンド検出 → 攻撃・移動・ジャンプ → 押し合い解消 → ヒット判定 → 勝敗 → 向き直し → アニメ進行。 */
    private void update() {
        // ダメージ数値ポップアップは決着 / ラウンド間でも上昇・フェードを続けるため、凍結ガードより前に進める
        // （KO を決めた一撃の数字が止まらず最後まで浮かぶ）。純粋な演出で戦闘結果には影響しない。
        updateDamagePopups();
        // マッチ決着後は全更新を凍結して結果表示の静止画を保つ。
        if (round.isFinished()) {
            return;
        }
        // ラウンド間インターバル中はファイター操作・判定を停止し、カウントダウンのみ進める。
        if (!round.isBetweenRounds()) {
            updateFighterInput(fighter1, p1Input, history1, 1);
            if (p2AiEnabled) {
                p2Ai.control(fighter2, fighter1);
            } else {
                updateFighterInput(fighter2, p2Input, history2, 2);
            }
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
            // 飛び道具（必殺技）の更新と命中処理（Task 20）。
            updateProjectiles();
        }
        // 勝敗 / ラウンド間カウントダウンを進める。
        round.update(fighter1, fighter2);
        // カウントダウン完了 → ファイターをスポーン位置にリセットして新ラウンド開始。
        if (round.consumeNextRoundReady()) {
            resetFighters();
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
        p2Ai.reset();
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
        String attackButton = lightPressed ? "light" : mediumPressed ? "medium" : heavyPressed ? "heavy" : null;
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
        boolean throwReq = in.isPressed(InputAction.THROW) && f.isGrounded() && !crouchHeld
                && f.getDef().getThrowMove() != null;
        if (throwReq) {
            attackButton = "throw";
        } else if (cmd != Command.NONE && anyAttack) {
            // 必殺技（Task 20/24）：コマンド成立かつ攻撃ボタンありなら対応する必殺技を発動。通常攻撃は抑止。
            Move special = findSpecialMove(f.getDef(), cmd);
            if (special != null && f.startSpecial(special)) {
                if (special.isProjectile()) {
                    spawnProjectile(f, special);
                }
                attackButton = null;
            }
        }
        f.update(dir, jump, attackButton, crouchHeld);
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

    /** 必殺技（飛び道具）の弾を発射者の前方に生成する（Task 20/24）。 */
    private void spawnProjectile(Fighter f, Move move) {
        if (move == null || !move.isProjectile()) {
            return;
        }
        Character d = f.getDef();
        float front = f.isFacingRight() ? f.getX() + d.getWidth() / 2f : f.getX() - d.getWidth() / 2f;
        float spawnX = f.isFacingRight()
                ? front + move.getHitboxOffsetX() + move.getHitboxWidth() / 2f
                : front - move.getHitboxOffsetX() - move.getHitboxWidth() / 2f;
        float spawnY = f.getY() + move.getHitboxOffsetY();
        float vx = (f.isFacingRight() ? 1f : -1f) * move.getProjectileSpeed();
        projectiles.add(new Projectile(spawnX, spawnY, vx, move.getHitboxWidth(), move.getHitboxHeight(),
                move.getDamage(), f));
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
                }
                spawnDamagePopup(before - target.getCurrentHp(), blocked,
                        p.getX(), target.getY() + target.getDef().getHeight() / 2f);
                p.kill();
            }
            if (!p.isAlive()) {
                it.remove();
            }
        }
    }

    /** attacker の active hitbox が defender に当たり、まだ未命中ならダメージ・のけぞりを適用する（Task 13 / Task 27 / Task 31 / Task 35）。 */
    private void resolveHit(Fighter attacker, Fighter defender) {
        if (attacker.hasAttackConnected() || !CollisionSystem.isHitting(attacker, defender)) {
            return;
        }
        // 投げ（Task 35）は地上の相手のみ掴める。空中の相手には不成立とし、未命中のまま（mark しない）後続フレームで再判定する。
        if (attacker.isThrowing() && !defender.isGrounded()) {
            return;
        }
        attacker.markAttackConnected();
        Hitbox hb = CollisionSystem.activeHitbox(attacker);
        int knockbackDir = defender.getX() >= attacker.getX() ? 1 : -1;
        int before = defender.getCurrentHp();
        // 投げはガード不能：ガード中でもフルダメージ＋長い hitstun を適用する（Task 35）。
        if (attacker.isThrowing()) {
            defender.applyThrow(hb.getDamage(), knockbackDir);
            spawnDamagePopup(before - defender.getCurrentHp(), false,
                    hb.getX() + hb.getWidth() / 2f, hb.getY() + hb.getHeight() / 2f);
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
        if (blocked) {
            // ガード成立：chip ダメージのみ（のけぞりなし）。
            defender.applyGuard(hb.getDamage(), knockbackDir);
        } else {
            defender.applyHit(hb.getDamage(), GameConstants.HITSTUN_FRAMES, knockbackDir);
        }
        // 実際に減った HP 量を命中位置（hitbox 中心）に数字で浮かべる。
        spawnDamagePopup(before - defender.getCurrentHp(), blocked,
                hb.getX() + hb.getWidth() / 2f, hb.getY() + hb.getHeight() / 2f);
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
