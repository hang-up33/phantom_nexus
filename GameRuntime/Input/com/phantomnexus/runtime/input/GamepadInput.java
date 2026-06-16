package com.phantomnexus.runtime.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;

import java.util.EnumSet;

/**
 * ゲームパッド（コントローラー）入力の集約（gdx-controllers のラッパー）。
 *
 * <p>PC に接続されたコントローラーをポーリングし、各スティック軸 / D-pad / ボタンを
 * 論理アクション（{@link InputAction}）へ射影する。{@link PlayerInput} に接続して
 * キーボード入力と OR 合成することで、コントローラーが繋がっていればそれでも操作でき、
 * 繋がっていなければ従来どおりキーボードで操作できる（ホットプラグ対応）。
 *
 * <p>キーボードの {@code isKeyJustPressed} に相当する「押された瞬間（立ち上がりエッジ）」は
 * gdx-controllers のポーリングでは取れないため、{@link #poll()} を毎フレーム 1 回呼び、
 * 前フレームとの差分でエッジを自前計算する。{@code poll()} はレンダリングスレッドの
 * フレーム先頭で呼ぶこと。
 *
 * <p>スロット割当：{@code slot=0} を 1P、{@code slot=1} を 2P が使う（接続順）。接続が無い
 * スロットは常に未押下を返す。コントローラー拡張やネイティブの読み込みに失敗した環境
 * （ヘッドレス等）では全クエリが false を返す（{@code failed} で以後の例外を抑止）＝
 * キーボードのみで従来どおり動作する。
 *
 * <p>ボタン割当（標準ゲームパッドのマッピングに準拠）：
 * 移動＝左スティック / D-pad、弱攻撃＝A、中攻撃＝B、強攻撃＝X（R1 でも可）、
 * 投げ＝Y（L1 でも可）。メニューでは方向で項目移動、A / START で決定、B / BACK で戻る。
 */
public class GamepadInput {

    /** スティックの傾きをデジタル方向とみなす閾値（デッドゾーン）。 */
    private static final float DEADZONE = 0.5f;
    /** 同時に扱うコントローラー数（1P / 2P）。 */
    private static final int SLOTS = 2;

    /** 各スロットの現フレーム押下中アクション。 */
    @SuppressWarnings("unchecked")
    private final EnumSet<InputAction>[] down = new EnumSet[SLOTS];
    /** 各スロットの前フレーム押下中アクション（エッジ計算用）。 */
    @SuppressWarnings("unchecked")
    private final EnumSet<InputAction>[] prev = new EnumSet[SLOTS];
    /** 各スロットの当該フレーム立ち上がりエッジ（前フレーム未押下 → 今フレーム押下）。 */
    @SuppressWarnings("unchecked")
    private final EnumSet<InputAction>[] edges = new EnumSet[SLOTS];
    /** START ボタンの現/エッジ状態（メニュー決定用）。 */
    private final boolean[] startDown = new boolean[SLOTS];
    private final boolean[] startEdge = new boolean[SLOTS];
    /** BACK ボタンの現/エッジ状態（メニュー戻る用）。 */
    private final boolean[] backDown = new boolean[SLOTS];
    private final boolean[] backEdge = new boolean[SLOTS];

    /** コントローラー拡張/ネイティブの読み込みに失敗したら true（以後ポーリングをスキップ）。 */
    private boolean failed = false;

    public GamepadInput() {
        for (int s = 0; s < SLOTS; s++) {
            down[s] = EnumSet.noneOf(InputAction.class);
            prev[s] = EnumSet.noneOf(InputAction.class);
            edges[s] = EnumSet.noneOf(InputAction.class);
        }
    }

    /**
     * 毎フレーム 1 回、接続中のコントローラーをポーリングして押下状態とエッジを更新する。
     * レンダリングスレッドのフレーム先頭で呼ぶこと。例外は握りつぶし（失敗後はスキップ）。
     */
    public void poll() {
        if (failed) {
            return;
        }
        try {
            Array<Controller> controllers = Controllers.getControllers();
            for (int s = 0; s < SLOTS; s++) {
                Controller c = s < controllers.size ? controllers.get(s) : null;
                EnumSet<InputAction> cur = readActions(c);
                // エッジ＝今フレーム押下 かつ 前フレーム未押下。
                edges[s] = EnumSet.copyOf(cur);
                edges[s].removeAll(prev[s]);
                prev[s] = EnumSet.copyOf(cur);
                down[s] = cur;

                ControllerMapping m = c != null ? c.getMapping() : null;
                boolean startNow = m != null && readButton(c, m.buttonStart);
                startEdge[s] = startNow && !startDown[s];
                startDown[s] = startNow;
                boolean backNow = m != null && readButton(c, m.buttonBack);
                backEdge[s] = backNow && !backDown[s];
                backDown[s] = backNow;
            }
        } catch (Throwable t) {
            // 拡張未導入 / ネイティブ読込失敗 / ヘッドレス等：以後はキーボードのみ（従来挙動）。
            failed = true;
        }
    }

    /** コントローラー {@code c} の現在の押下アクション集合を読む（接続無し / 例外は空集合）。 */
    private EnumSet<InputAction> readActions(Controller c) {
        EnumSet<InputAction> set = EnumSet.noneOf(InputAction.class);
        if (c == null) {
            return set;
        }
        try {
            ControllerMapping m = c.getMapping();
            if (m == null) {
                return set;
            }
            float ax = readAxis(c, m.axisLeftX);
            float ay = readAxis(c, m.axisLeftY);
            // 左スティックと D-pad のどちらでも方向入力できる。Y 軸は上が負（SDL 準拠）。
            if (readButton(c, m.buttonDpadLeft) || ax < -DEADZONE) {
                set.add(InputAction.LEFT);
            }
            if (readButton(c, m.buttonDpadRight) || ax > DEADZONE) {
                set.add(InputAction.RIGHT);
            }
            if (readButton(c, m.buttonDpadUp) || ay < -DEADZONE) {
                set.add(InputAction.UP);
            }
            if (readButton(c, m.buttonDpadDown) || ay > DEADZONE) {
                set.add(InputAction.DOWN);
            }
            if (readButton(c, m.buttonA)) {
                set.add(InputAction.ATTACK_LIGHT);
            }
            if (readButton(c, m.buttonB)) {
                set.add(InputAction.ATTACK_MEDIUM);
            }
            if (readButton(c, m.buttonX) || readButton(c, m.buttonR1)) {
                set.add(InputAction.ATTACK_HEAVY);
            }
            if (readButton(c, m.buttonY) || readButton(c, m.buttonL1)) {
                set.add(InputAction.THROW);
            }
        } catch (Throwable t) {
            // 個別コントローラーの読み取り失敗は空集合扱い（他スロットには影響させない）。
            return EnumSet.noneOf(InputAction.class);
        }
        return set;
    }

    private boolean readButton(Controller c, int code) {
        return c != null && code >= 0 && c.getButton(code);
    }

    /** 軸値を読む。未マッピング（コード &lt; 0）なら中立（0）を返す（readButton と同じ防御）。 */
    private float readAxis(Controller c, int code) {
        return (c != null && code >= 0) ? c.getAxis(code) : 0f;
    }

    /** スロット {@code slot} のコントローラーでアクションが押下中か（移動 / しゃがみ向け）。 */
    public boolean isDown(int slot, InputAction action) {
        return slot >= 0 && slot < SLOTS && down[slot].contains(action);
    }

    /** スロット {@code slot} のコントローラーでアクションが押された瞬間か（ジャンプ / 攻撃向け）。 */
    public boolean isJustPressed(int slot, InputAction action) {
        return slot >= 0 && slot < SLOTS && edges[slot].contains(action);
    }

    // --- メニュー操作（どのスロットのコントローラーでも操作できるよう全スロットを OR） ---

    private boolean anyEdge(InputAction action) {
        for (int s = 0; s < SLOTS; s++) {
            if (edges[s].contains(action)) {
                return true;
            }
        }
        return false;
    }

    /** メニュー：上へ（カーソル移動）。 */
    public boolean menuUp() {
        return anyEdge(InputAction.UP);
    }

    /** メニュー：下へ（カーソル移動）。 */
    public boolean menuDown() {
        return anyEdge(InputAction.DOWN);
    }

    /** メニュー：左へ（カーソル移動）。 */
    public boolean menuLeft() {
        return anyEdge(InputAction.LEFT);
    }

    /** メニュー：右へ（カーソル移動）。 */
    public boolean menuRight() {
        return anyEdge(InputAction.RIGHT);
    }

    /** メニュー：決定（A / START）。 */
    public boolean menuConfirm() {
        return anyEdge(InputAction.ATTACK_LIGHT) || startEdge[0] || startEdge[1];
    }

    /** メニュー：戻る / キャンセル（B / BACK）。 */
    public boolean menuCancel() {
        return anyEdge(InputAction.ATTACK_MEDIUM) || backEdge[0] || backEdge[1];
    }
}
