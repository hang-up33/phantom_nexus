package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.BattleRules;

/**
 * ラウンドの進行・勝敗判定（Task 14）。
 *
 * <p>MVP は 1 ラウンド制。毎フレーム制限時間を減らし、以下で決着する（[docs/BattleSystem.md](../../../../../../docs/BattleSystem.md)）：
 * <ul>
 *   <li><b>KO</b>：いずれかの HP が 0 になった瞬間。両者同時 KO は引き分け。</li>
 *   <li><b>タイムアップ</b>：制限時間が尽きたら HP 残量が多い側の勝ち。同値は引き分け。</li>
 * </ul>
 * 決着後は {@link #isFinished()} が true になり、呼び出し側（Core）は入力・物理の更新を止めて結果表示へ移る。
 * 本クラスは {@link Fighter} を読み取るのみで変更しない（勝敗の単一の判定点）。
 */
public final class RoundManager {

    /** 勝者。 */
    public enum Winner { NONE, P1, P2, DRAW }

    /** 決着理由。 */
    public enum FinishReason { NONE, KO, TIMEOUT }

    private final int totalFrames;
    private int remainingFrames;
    private boolean finished;
    private Winner winner = Winner.NONE;
    private FinishReason reason = FinishReason.NONE;

    public RoundManager(BattleRules rules) {
        this.totalFrames = Math.max(0, rules.getTimeLimitSeconds()) * GameConstants.TARGET_FPS;
        this.remainingFrames = totalFrames;
    }

    /**
     * 1 フレーム進める。KO を最優先で判定し、無ければ制限時間を減らしてタイムアップを判定する。
     * 決着済みなら何もしない。
     */
    public void update(Fighter p1, Fighter p2) {
        if (finished) {
            return;
        }
        boolean ko1 = p1.isKO();
        boolean ko2 = p2.isKO();
        if (ko1 || ko2) {
            finished = true;
            reason = FinishReason.KO;
            winner = (ko1 && ko2) ? Winner.DRAW : (ko2 ? Winner.P1 : Winner.P2);
            return;
        }
        if (remainingFrames > 0) {
            remainingFrames--;
        }
        if (remainingFrames <= 0) {
            finished = true;
            reason = FinishReason.TIMEOUT;
            winner = decideByHp(p1, p2);
        }
    }

    private static Winner decideByHp(Fighter p1, Fighter p2) {
        int h1 = p1.getCurrentHp();
        int h2 = p2.getCurrentHp();
        if (h1 == h2) {
            return Winner.DRAW;
        }
        return h1 > h2 ? Winner.P1 : Winner.P2;
    }

    /** 決着したか。 */
    public boolean isFinished() {
        return finished;
    }

    public Winner getWinner() {
        return winner;
    }

    public FinishReason getReason() {
        return reason;
    }

    /** 残り時間（秒, 切り上げ）。HUD タイマー表示に使用。 */
    public int getRemainingSeconds() {
        return (remainingFrames + GameConstants.TARGET_FPS - 1) / GameConstants.TARGET_FPS;
    }
}
