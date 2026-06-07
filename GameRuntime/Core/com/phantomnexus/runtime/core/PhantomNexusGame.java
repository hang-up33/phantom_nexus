package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;
import com.phantomnexus.runtime.rendering.GameRenderer;

/**
 * Phantom Nexus アプリケーション本体（ゲームループ / ライフサイクル）。
 *
 * <p>Core はライフサイクル（create/render/resize/dispose）の制御に専念し、
 * 実際の描画は {@link GameRenderer}（Rendering）へ委譲する（Task 3）。
 * Task 5 でプレイヤー入力（{@link PlayerInput}）を毎フレーム取得し、現在の入力状態を
 * 画面表示して入力配線を確認できるようにした（移動 / ジャンプ / 攻撃は後続タスクで接続）。
 */
public class PhantomNexusGame extends ApplicationAdapter {

    private GameRenderer renderer;
    private PlayerInput player1;
    private String controlsHint;

    @Override
    public void create() {
        renderer = new GameRenderer();
        player1 = PlayerInput.player1Defaults();
        controlsHint = "P1   " + player1.describe();
    }

    @Override
    public void render() {
        renderer.render(controlsHint, "Active: " + activeActions(player1));
    }

    /** 押下中の論理アクションを空白区切りで返す（無ければ "-"）。入力配線の動作確認用。 */
    private static String activeActions(PlayerInput input) {
        StringBuilder sb = new StringBuilder();
        for (InputAction action : InputAction.values()) {
            if (input.isDown(action)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(action.name());
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
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
