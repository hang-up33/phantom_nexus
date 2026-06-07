package com.phantomnexus.runtime.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;
import com.phantomnexus.runtime.rendering.GameRenderer;
import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;

/**
 * Phantom Nexus アプリケーション本体（ゲームループ / ライフサイクル）。
 *
 * <p>Core はライフサイクル（create/render/resize/dispose）の制御に専念し、
 * 実際の描画は {@link GameRenderer}（Rendering）へ委譲する（Task 3）。
 * Task 5 でプレイヤー入力（{@link PlayerInput}）を、Task 6 で 2 体のキャラクター定義
 * （{@link Character}）を保持し、固定位置に描画する。サンプル定義はコード生成の暫定で、
 * Task 16 で {@code Shared/Schema} の JSON ローダ供給に差し替える。位置の可変化（移動）は Task 7。
 */
public class PhantomNexusGame extends ApplicationAdapter {

    private GameRenderer renderer;
    private PlayerInput player1;
    private String controlsHint;
    private Character player1Char;
    private Character player2Char;

    @Override
    public void create() {
        renderer = new GameRenderer();
        player1 = PlayerInput.player1Defaults();
        controlsHint = "P1   " + player1.describe();
        // 暫定のサンプルキャラクター定義（Task 16 で JSON 読込に差し替え）。
        player1Char = new Character("fighter001", "Aoi", 1000, 4.0f, 12.0f, 100f, 240f);
        player2Char = new Character("fighter002", "Akane", 1000, 4.0f, 12.0f, 100f, 240f);
    }

    @Override
    public void render() {
        renderer.renderScene(
                player1Char, GameConstants.P1_SPAWN_X,
                player2Char, GameConstants.P2_SPAWN_X,
                controlsHint, "Active: " + activeActions(player1));
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
