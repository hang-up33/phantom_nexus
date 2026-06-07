package com.phantomnexus.runtime.input;

/**
 * プレイヤーの論理入力アクション（物理キーから切り離した抽象）。
 *
 * <p>ゲームロジック（移動 / ジャンプ / 攻撃 など）は物理キーコードではなく本 enum を参照する。
 * 物理キーとの対応付けは {@link PlayerInput} が保持し、キー割当の変更がロジックに波及しない
 * ようにする。MVP では方向 4 種 + 攻撃 1 種。攻撃ボタンや必殺技コマンドは後続タスクで拡張する。
 */
public enum InputAction {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    ATTACK
}
