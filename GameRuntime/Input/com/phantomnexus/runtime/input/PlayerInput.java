package com.phantomnexus.runtime.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.Map;

/**
 * 1 プレイヤー分のキーボード入力抽象（Task 5: 入力処理作成）。
 *
 * <p>物理キー（{@link Gdx#input} の {@code isKeyPressed} / {@code isKeyJustPressed}）を
 * 論理アクション（{@link InputAction}）へ射影する。ゲームロジックはキー定数を直接触らず、
 * 本クラスの {@link #isDown(InputAction)} / {@link #isPressed(InputAction)} 経由で入力を参照する。
 * 後続タスク（移動 / ジャンプ / 攻撃 / AI 差し替え）はすべてこの抽象に依存する。
 */
public class PlayerInput {

    private final Map<InputAction, Integer> bindings;

    public PlayerInput(Map<InputAction, Integer> bindings) {
        // enum 型を明示して空 Map（設定ロード前・未割当状態）でも初期化できるようにする。
        // new EnumMap<>(bindings) は空 Map で enum 型を推論できず IllegalArgumentException になる。
        this.bindings = new EnumMap<>(InputAction.class);
        this.bindings.putAll(bindings);
    }

    /** プレイヤー 1 の既定割当（WASD で移動・W でジャンプ・S でしゃがみ・F で攻撃）。 */
    public static PlayerInput player1Defaults() {
        EnumMap<InputAction, Integer> b = new EnumMap<>(InputAction.class);
        b.put(InputAction.LEFT, Input.Keys.A);
        b.put(InputAction.RIGHT, Input.Keys.D);
        b.put(InputAction.UP, Input.Keys.W);
        b.put(InputAction.DOWN, Input.Keys.S);
        b.put(InputAction.ATTACK, Input.Keys.F);
        return new PlayerInput(b);
    }

    /** プレイヤー 2 の既定割当（方向キーで移動・右 Ctrl で攻撃）。2 体対戦（Task 22）で使用。 */
    public static PlayerInput player2Defaults() {
        EnumMap<InputAction, Integer> b = new EnumMap<>(InputAction.class);
        b.put(InputAction.LEFT, Input.Keys.LEFT);
        b.put(InputAction.RIGHT, Input.Keys.RIGHT);
        b.put(InputAction.UP, Input.Keys.UP);
        b.put(InputAction.DOWN, Input.Keys.DOWN);
        b.put(InputAction.ATTACK, Input.Keys.CONTROL_RIGHT);
        return new PlayerInput(b);
    }

    /** 当該フレームでアクションのキーが押下中か（押しっぱなし検出。移動 / しゃがみ向け）。 */
    public boolean isDown(InputAction action) {
        Integer key = bindings.get(action);
        return key != null && Gdx.input.isKeyPressed(key);
    }

    /** 当該フレームでアクションのキーが押された瞬間か（立ち上がりエッジ検出。ジャンプ / 攻撃向け）。 */
    public boolean isPressed(InputAction action) {
        Integer key = bindings.get(action);
        return key != null && Gdx.input.isKeyJustPressed(key);
    }

    /** アクションに割り当てられた物理キーコード（未割当は {@code -1}）。 */
    public int getKey(InputAction action) {
        Integer key = bindings.get(action);
        return key != null ? key : -1;
    }

    /** 現在のキー割当を人間可読な 1 行で返す（操作ガイド表示用）。 */
    public String describe() {
        return "Move " + keyName(InputAction.LEFT) + "/" + keyName(InputAction.RIGHT)
                + "   Jump " + keyName(InputAction.UP)
                + "   Crouch " + keyName(InputAction.DOWN)
                + "   Attack " + keyName(InputAction.ATTACK);
    }

    private String keyName(InputAction action) {
        int key = getKey(action);
        return key >= 0 ? Input.Keys.toString(key) : "-";
    }
}
