package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.phantomnexus.runtime.battle.CollisionSystem;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.runtime.debug.ScreenshotController;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;
import com.phantomnexus.runtime.rendering.FighterAnimator;
import com.phantomnexus.runtime.rendering.GameRenderer;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Move;

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
    private String controlsHint;
    private ScreenshotController screenshot;

    @Override
    public void create() {
        renderer = new GameRenderer();
        // ヘッドレス自動スクショ（phantom.screenshot.* 指定時のみ有効。通常起動には無影響）。
        screenshot = new ScreenshotController();
        p1Input = PlayerInput.player1Defaults();
        p2Input = PlayerInput.player2Defaults();
        // 過渡状態の撮影用に、指定があれば起動時から入力を押下状態に固定する（通常は空＝無影響）。
        p1Input.setForcedHold(screenshot.heldActions(1));
        p2Input.setForcedHold(screenshot.heldActions(2));
        // 暫定のサンプルキャラクター定義（Task 16 で JSON 読込に差し替え）。
        Character aoi = new Character("fighter001", "Aoi", 1000, 4.0f, 12.0f, 100f, 240f);
        Character akane = new Character("fighter002", "Akane", 1000, 4.0f, 12.0f, 100f, 240f);
        // 暫定の通常攻撃（Task 11）。startup 8 / active 6 / recovery 16 フレーム、前方へ伸びる hitbox。
        // damage / hitbox は Task 13 / 12 で使用。Task 16 で JSON の moves[] から供給する。
        aoi.setNormalAttack(samplePunch());
        akane.setNormalAttack(samplePunch());
        fighter1 = new Fighter(aoi, GameConstants.P1_SPAWN_X, true);
        fighter2 = new Fighter(akane, GameConstants.P2_SPAWN_X, false);
        // アニメーション状態機械（Task 9）。各ファイターの実行時状態から idle/walk/jump を導出する。
        animator1 = new FighterAnimator();
        animator2 = new FighterAnimator();
        controlsHint = "P1 " + p1Input.describe() + "      P2 Arrows + RCtrl";
    }

    @Override
    public void render() {
        update();
        renderer.renderScene(fighter1, fighter2, animator1, animator2, controlsHint, statusLine());
        // 描画後にフレームバッファを撮影（撮影モード時のみ。完了したら自動終了）。
        screenshot.maybeCapture();
    }

    /** 入力 → 攻撃・移動・ジャンプ → 押し合い解消 → ヒット判定 → 向き直し → アニメ進行の 1 フレーム更新。 */
    private void update() {
        fighter1.update(moveDir(p1Input), p1Input.isPressed(InputAction.UP), p1Input.isPressed(InputAction.ATTACK));
        fighter2.update(moveDir(p2Input), p2Input.isPressed(InputAction.UP), p2Input.isPressed(InputAction.ATTACK));
        // 押し合い解消（pushbox の重なりを左右へ分離）。
        CollisionSystem.resolvePush(fighter1, fighter2);
        // ヒット判定（active hitbox × 相手 hurtbox）。多段ヒット防止のため攻撃ごと 1 回だけ確定する。
        // ダメージ適用・のけぞりは Task 13 で本判定結果に接続する。
        resolveHit(fighter1, fighter2);
        resolveHit(fighter2, fighter1);
        fighter1.faceTowards(fighter2);
        fighter2.faceTowards(fighter1);
        // 描画状態の更新（移動・向き確定後にファイター状態からアニメ状態を導出して 1 tick 進める）。
        animator1.update(fighter1);
        animator2.update(fighter2);
    }

    /** attacker の active hitbox が defender に当たり、まだ未命中なら命中確定（ダメージは Task 13）。 */
    private static void resolveHit(Fighter attacker, Fighter defender) {
        if (!attacker.hasAttackConnected() && CollisionSystem.isHitting(attacker, defender)) {
            attacker.markAttackConnected();
        }
    }

    /** 暫定の通常攻撃（パンチ）。前方の前面・足元中段に伸びる hitbox（向きで左右反転）。 */
    private static Move samplePunch() {
        return new Move("standing_punch", "ATTACK", 80, 8, 6, 16,
                0f, 120f, 90f, 40f);
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

    /** 各ファイターの座標・向き・接地状態を 1 行で返す（移動 / ジャンプの動作確認用 HUD）。 */
    private String statusLine() {
        return String.format(
                "%s x=%.0f y=%.0f %s%s    %s x=%.0f y=%.0f %s%s",
                fighter1.getDef().getName(), fighter1.getX(), fighter1.getY(), facingArrow(fighter1), airTag(fighter1),
                fighter2.getDef().getName(), fighter2.getX(), fighter2.getY(), facingArrow(fighter2), airTag(fighter2));
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
