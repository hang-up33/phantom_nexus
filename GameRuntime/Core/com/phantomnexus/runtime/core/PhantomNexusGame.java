package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.phantomnexus.runtime.battle.Fighter;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;
import com.phantomnexus.runtime.rendering.GameRenderer;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;

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
    private String controlsHint;

    @Override
    public void create() {
        renderer = new GameRenderer();
        p1Input = PlayerInput.player1Defaults();
        p2Input = PlayerInput.player2Defaults();
        // 暫定のサンプルキャラクター定義（Task 16 で JSON 読込に差し替え）。
        Character aoi = new Character("fighter001", "Aoi", 1000, 4.0f, 12.0f, 100f, 240f);
        Character akane = new Character("fighter002", "Akane", 1000, 4.0f, 12.0f, 100f, 240f);
        fighter1 = new Fighter(aoi, GameConstants.P1_SPAWN_X, true);
        fighter2 = new Fighter(akane, GameConstants.P2_SPAWN_X, false);
        controlsHint = "P1 " + p1Input.describe() + "      P2 Arrows + RCtrl";
    }

    @Override
    public void render() {
        update();
        renderer.renderScene(fighter1, fighter2, controlsHint, statusLine());
    }

    /** 入力 → 移動・ジャンプ → 向き直しの 1 フレーム更新。 */
    private void update() {
        fighter1.update(moveDir(p1Input), p1Input.isPressed(InputAction.UP));
        fighter2.update(moveDir(p2Input), p2Input.isPressed(InputAction.UP));
        fighter1.faceTowards(fighter2);
        fighter2.faceTowards(fighter1);
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
