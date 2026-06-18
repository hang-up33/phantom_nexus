package com.phantomnexus.runtime.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 1 プレイヤー分のキーボード入力抽象（Task 5: 入力処理作成）。
 *
 * <p>物理キー（{@link Gdx#input} の {@code isKeyPressed} / {@code isKeyJustPressed}）を
 * 論理アクション（{@link InputAction}）へ射影する。Task 24 で攻撃ボタンを弱/中/強に拡張。
 * ゲームロジックはキー定数を直接触らず、
 * 本クラスの {@link #isDown(InputAction)} / {@link #isPressed(InputAction)} 経由で入力を参照する。
 */
public class PlayerInput {

    private final Map<InputAction, Integer> bindings;
    /** 強制押下中のアクション（ヘッドレススクショの過渡状態撮影用。通常は空）。 */
    private EnumSet<InputAction> forcedHold = EnumSet.noneOf(InputAction.class);
    /** 強制押下の「押された瞬間」を 1 回だけ供給するための未消費エッジ集合。 */
    private final EnumSet<InputAction> forcedEdgePending = EnumSet.noneOf(InputAction.class);
    /** 接続済みコントローラー入力（任意）。null ならキーボードのみ。 */
    private GamepadInput gamepad = null;
    /** このプレイヤーが使うコントローラーのスロット（0=1P / 1=2P）。 */
    private int gamepadSlot = -1;

    public PlayerInput(Map<InputAction, Integer> bindings) {
        this.bindings = new EnumMap<>(InputAction.class);
        this.bindings.putAll(bindings);
    }

    /**
     * このプレイヤーにコントローラー入力を接続する（任意）。接続後はキーボードと OR 合成され、
     * どちらでも操作できる。{@code gamepad} の {@code poll()} はフレーム先頭で別途呼ぶこと。
     *
     * @param gamepad 集約コントローラー入力（null で切断＝キーボードのみ）
     * @param slot    使用スロット（0=1P / 1=2P）
     */
    public void attachGamepad(GamepadInput gamepad, int slot) {
        this.gamepad = gamepad;
        this.gamepadSlot = slot;
    }

    /**
     * プレイヤー 1 の既定割当。
     * 移動: WASD / ジャンプ: W / しゃがみ: S
     * 攻撃: 弱=F / 中=G / 強=H / 投げ=T
     */
    public static PlayerInput player1Defaults() {
        EnumMap<InputAction, Integer> b = new EnumMap<>(InputAction.class);
        b.put(InputAction.LEFT, Input.Keys.A);
        b.put(InputAction.RIGHT, Input.Keys.D);
        b.put(InputAction.UP, Input.Keys.W);
        b.put(InputAction.DOWN, Input.Keys.S);
        b.put(InputAction.ATTACK_LIGHT, Input.Keys.F);
        b.put(InputAction.ATTACK_MEDIUM, Input.Keys.G);
        b.put(InputAction.ATTACK_HEAVY, Input.Keys.H);
        b.put(InputAction.THROW, Input.Keys.T);
        return new PlayerInput(b);
    }

    /**
     * プレイヤー 2 の既定割当。
     * 移動: 方向キー / ジャンプ: ↑ / しゃがみ: ↓
     * 攻撃: 弱=Numpad1 / 中=Numpad2 / 強=Numpad3 / 投げ=Numpad0
     */
    public static PlayerInput player2Defaults() {
        EnumMap<InputAction, Integer> b = new EnumMap<>(InputAction.class);
        b.put(InputAction.LEFT, Input.Keys.LEFT);
        b.put(InputAction.RIGHT, Input.Keys.RIGHT);
        b.put(InputAction.UP, Input.Keys.UP);
        b.put(InputAction.DOWN, Input.Keys.DOWN);
        b.put(InputAction.ATTACK_LIGHT, Input.Keys.NUMPAD_1);
        b.put(InputAction.ATTACK_MEDIUM, Input.Keys.NUMPAD_2);
        b.put(InputAction.ATTACK_HEAVY, Input.Keys.NUMPAD_3);
        b.put(InputAction.THROW, Input.Keys.NUMPAD_0);
        return new PlayerInput(b);
    }

    /**
     * 過渡状態スクショ用に、アクションを「押下状態」に固定する（テスト/撮影専用）。
     *
     * <p>{@link #isDown} は対象アクションを常時 true にし、{@link #isPressed}（立ち上がり）は
     * <strong>押下開始フレームの 1 回だけ</strong> true を返す（ジャンプ/攻撃を一度だけ発動させる）。
     *
     * <p>毎フレーム呼ばれる（タイムド入力スクリプトと {@code -k} 併用）ことを前提に、
     * 立ち上がりエッジは「前フレームは未押下で今フレーム押下」になったアクションにのみ供給する。
     * 押下が継続中のアクションはエッジを再生成しないため、{@code -k p1.attack} のような基礎 hold を
     * スクリプトと併用しても毎フレーム発火しない（離されたアクションの未消費エッジは破棄する）。
     * 通常プレイでは呼ばれない。
     */
    public void setForcedHold(Set<InputAction> actions) {
        EnumSet<InputAction> next = EnumSet.noneOf(InputAction.class);
        if (actions != null) {
            next.addAll(actions);
        }
        // 新たに押下された（前フレーム未押下）アクションだけを立ち上がりエッジとして供給する。
        for (InputAction action : next) {
            if (!forcedHold.contains(action)) {
                forcedEdgePending.add(action);
            }
        }
        // 押下が解除されたアクションの未消費エッジは破棄する。
        forcedEdgePending.retainAll(next);
        forcedHold = next;
    }

    /** 当該フレームでアクションのキーが押下中か（押しっぱなし検出。移動 / しゃがみ向け）。 */
    public boolean isDown(InputAction action) {
        if (forcedHold.contains(action)) {
            return true;
        }
        Integer key = bindings.get(action);
        if (key != null && Gdx.input.isKeyPressed(key)) {
            return true;
        }
        // コントローラーが接続されていればそちらの押下も拾う（キーボードと OR）。
        return gamepad != null && gamepad.isDown(gamepadSlot, action);
    }

    /** 当該フレームでアクションのキーが押された瞬間か（立ち上がりエッジ検出。ジャンプ / 攻撃向け）。 */
    public boolean isPressed(InputAction action) {
        if (forcedHold.contains(action)) {
            // 強制押下：最初の呼び出しだけ立ち上がりとして消費し、以降は false（押しっぱなし扱い）。
            // 撮影/リプレイの決定性を保つため、強制押下中はコントローラーを参照しない。
            return forcedEdgePending.remove(action);
        }
        Integer key = bindings.get(action);
        if (key != null && Gdx.input.isKeyJustPressed(key)) {
            return true;
        }
        // コントローラーが接続されていれば、その立ち上がりエッジも拾う（キーボードと OR）。
        return gamepad != null && gamepad.isJustPressed(gamepadSlot, action);
    }

    /** アクションに割り当てられた物理キーコード（未割当は {@code -1}）。 */
    public int getKey(InputAction action) {
        Integer key = bindings.get(action);
        return key != null ? key : -1;
    }

    /** アクションのキー割当を上書きする（キーコンフィグ画面用）。 */
    public void setBinding(InputAction action, int keycode) {
        bindings.put(action, keycode);
    }

    /** 現在のキー割当を人間可読な 1 行で返す（操作ガイド表示用）。 */
    public String describe() {
        return "Move " + keyName(InputAction.LEFT) + "/" + keyName(InputAction.RIGHT)
                + "  Jump " + keyName(InputAction.UP)
                + "  Crouch " + keyName(InputAction.DOWN)
                + "  L:" + keyName(InputAction.ATTACK_LIGHT)
                + "  M:" + keyName(InputAction.ATTACK_MEDIUM)
                + "  H:" + keyName(InputAction.ATTACK_HEAVY)
                + "  Throw:" + keyName(InputAction.THROW);
    }

    private String keyName(InputAction action) {
        int key = getKey(action);
        return key >= 0 ? Input.Keys.toString(key) : "-";
    }
}
