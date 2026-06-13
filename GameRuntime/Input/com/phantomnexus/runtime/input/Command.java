package com.phantomnexus.runtime.input;

/**
 * 検出されるコマンド入力の種類（Task 19）。
 *
 * <p>MVP は第一設計書の例（波動拳 / 溜め / 同時押し）に対応する 3 種＋なし。実際の必殺技発動は
 * Task 20（必殺技ステート）が本検出結果を受けて行う。MVP では単キー通常攻撃（Task 11）と併存する。
 */
public enum Command {
    /** コマンド未成立。 */
    NONE,
    /** 波動拳：下 → 前 + 攻撃（QCF）。 */
    HADOUKEN,
    /** 溜め：後を一定フレーム溜めてから前 + 攻撃。 */
    CHARGE_SHOT,
    /** 同時押し：下 + 攻撃を同フレームに。 */
    DOWN_ATTACK,
    /** スーパー必殺技：波動拳コマンドを 2 回（236236）＋攻撃。満タンメーター消費で発動（Task 108）。 */
    SUPER;

    /** 表示用ラベル。 */
    public String label() {
        switch (this) {
            case HADOUKEN:
                return "HADOUKEN (236+A)";
            case CHARGE_SHOT:
                return "CHARGE (hold 4, 6+A)";
            case DOWN_ATTACK:
                return "DOWN+A";
            case SUPER:
                return "SUPER (236236+A)";
            default:
                return "-";
        }
    }
}
