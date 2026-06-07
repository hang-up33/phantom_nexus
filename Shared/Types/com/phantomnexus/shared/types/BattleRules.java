package com.phantomnexus.shared.types;

/**
 * 対戦ルールの静的定義（データの単一の真実）。Task 14: ラウンド勝敗判定。
 *
 * <p>ラウンド数・制限時間など対戦全体のルールを表す POJO。MVP は 1 ラウンド制で、制限時間内に
 * 相手を KO すれば勝ち、時間切れなら HP 残量が多い側を勝ちとする（[docs/BattleSystem.md](../../../../../../docs/BattleSystem.md)）。
 * HP 上限はキャラ定義（{@link Character#getHp()}）側に持つ。供給元は MVP ではコード生成、Task 16 以降で
 * JSON ローダへ差し替える（LibGDX {@code Json} がリフレクションで設定できるよう非 final・無引数コンストラクタ）。
 */
public class BattleRules {

    private int timeLimitSeconds;
    private int rounds;

    /** JSON / リフレクション用の無引数コンストラクタ。 */
    public BattleRules() {
    }

    public BattleRules(int timeLimitSeconds, int rounds) {
        this.timeLimitSeconds = timeLimitSeconds;
        this.rounds = rounds;
    }

    /** MVP 既定：制限時間 99 秒・1 ラウンド。 */
    public static BattleRules defaults() {
        return new BattleRules(99, 1);
    }

    /** 1 ラウンドの制限時間（秒）。 */
    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    /** ラウンド数（MVP は 1）。 */
    public int getRounds() {
        return rounds;
    }
}
