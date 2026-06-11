package com.phantomnexus.runtime.input;

/**
 * プレイヤーの論理入力アクション（物理キーから切り離した抽象）。
 *
 * <p>ゲームロジック（移動 / ジャンプ / 攻撃 など）は物理キーコードではなく本 enum を参照する。
 * 物理キーとの対応付けは {@link PlayerInput} が保持し、キー割当の変更がロジックに波及しない
 * ようにする。Task 24 で攻撃ボタンを弱 / 中 / 強の 3 種に拡張した。Task 35 で投げ（{@link #THROW}）を追加。
 */
public enum InputAction {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    ATTACK_LIGHT,
    ATTACK_MEDIUM,
    ATTACK_HEAVY,
    /** 投げ（ガード不能の近接掴み。Task 35）。地上・立ちで押すと投げを発動する。 */
    THROW
}
