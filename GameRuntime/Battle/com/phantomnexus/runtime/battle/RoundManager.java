package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.BattleRules;

/**
 * ラウンドの進行・勝敗判定（Task 14 / Task 26: 複数ラウンド制）。
 *
 * <p>先取 {@code roundsToWin} ラウンドを制したプレイヤーがマッチ勝者になる（ベスト・オブ 3 なら 2 先取）。
 * 各ラウンドは KO（いずれかの HP が 0）またはタイムアップ（制限時間切れで HP 多い側が勝ち）で決着し、
 * マッチ未確定なら {@link #BETWEEN_ROUND_FRAMES} フレームのインターバルを経て次ラウンドへ自動移行する。
 * マッチ確定後は {@link #isFinished()} が true になり、Core 側は全更新を凍結して結果表示へ移る。
 *
 * @see <a href="../../../../../../docs/BattleSystem.md">docs/BattleSystem.md</a>
 */
public final class RoundManager {

    /** ラウンド間のインターバル（フレーム数）。2 秒 @ 60fps。 */
    public static final int BETWEEN_ROUND_FRAMES = 120;

    /** 勝者。 */
    public enum Winner { NONE, P1, P2, DRAW }

    /** 決着理由。 */
    public enum FinishReason { NONE, KO, TIMEOUT }

    private final int roundsToWin;
    private final int maxRounds; // = 2 * roundsToWin - 1：全ラウンドが引き分けでも終了を保証
    private final int totalFrames;

    // Win counters
    private int p1Wins = 0;
    private int p2Wins = 0;
    private int currentRound = 1;

    // Current round state
    private int remainingFrames;
    private boolean roundFinished = false;
    private Winner roundWinner = Winner.NONE;
    private FinishReason roundReason = FinishReason.NONE;

    // Match state
    private boolean matchOver = false;
    private Winner matchWinner = Winner.NONE;
    private FinishReason matchReason = FinishReason.NONE;

    // Between-round state
    private int betweenCountdown = 0;
    private boolean nextRoundReady = false;

    public RoundManager(BattleRules rules) {
        this.roundsToWin = rules.getRoundsToWin();
        this.maxRounds = 2 * roundsToWin - 1;
        this.totalFrames = Math.max(0, rules.getTimeLimitSeconds()) * GameConstants.TARGET_FPS;
        this.remainingFrames = totalFrames;
    }

    /**
     * 1 フレーム進める。ラウンド間カウントダウン中は戦闘を停止してカウントを減らし、
     * 0 に達したら次ラウンド準備フラグを立てる。アクティブラウンド中は KO と制限時間を判定する。
     */
    public void update(Fighter p1, Fighter p2) {
        if (matchOver) {
            return;
        }
        if (betweenCountdown > 0) {
            betweenCountdown--;
            if (betweenCountdown == 0) {
                nextRoundReady = true;
                startNewRound();
            }
            return;
        }
        if (roundFinished) {
            return;
        }
        boolean ko1 = p1.isKO();
        boolean ko2 = p2.isKO();
        if (ko1 || ko2) {
            roundFinished = true;
            roundReason = FinishReason.KO;
            roundWinner = (ko1 && ko2) ? Winner.DRAW : (ko2 ? Winner.P1 : Winner.P2);
            finishRound();
            return;
        }
        if (remainingFrames > 0) {
            remainingFrames--;
        }
        if (remainingFrames <= 0) {
            roundFinished = true;
            roundReason = FinishReason.TIMEOUT;
            roundWinner = decideByHp(p1, p2);
            finishRound();
        }
    }

    private void finishRound() {
        if (roundWinner == Winner.P1) {
            p1Wins++;
        } else if (roundWinner == Winner.P2) {
            p2Wins++;
        }
        if (p1Wins >= roundsToWin || p2Wins >= roundsToWin || currentRound >= maxRounds) {
            matchOver = true;
            matchWinner = p1Wins > p2Wins ? Winner.P1 : (p2Wins > p1Wins ? Winner.P2 : Winner.DRAW);
            matchReason = roundReason;
        } else {
            betweenCountdown = BETWEEN_ROUND_FRAMES;
        }
    }

    private void startNewRound() {
        currentRound++;
        remainingFrames = totalFrames;
        roundFinished = false;
        roundWinner = Winner.NONE;
        roundReason = FinishReason.NONE;
    }

    private static Winner decideByHp(Fighter p1, Fighter p2) {
        int h1 = p1.getCurrentHp();
        int h2 = p2.getCurrentHp();
        if (h1 == h2) {
            return Winner.DRAW;
        }
        return h1 > h2 ? Winner.P1 : Winner.P2;
    }

    /**
     * 次ラウンド準備フラグを消費して返す（1 フレームに 1 回だけ true になる）。
     * Core はこのフラグを見てファイターをリセットする。
     */
    public boolean consumeNextRoundReady() {
        if (nextRoundReady) {
            nextRoundReady = false;
            return true;
        }
        return false;
    }

    /** マッチが完全に終了したか（いずれかのプレイヤーが {@code roundsToWin} ラウンドを先取）。 */
    public boolean isFinished() {
        return matchOver;
    }

    /** ラウンド間カウントダウン中か。この間は戦闘を停止し、ラウンド結果バナーを表示する。 */
    public boolean isBetweenRounds() {
        return !matchOver && betweenCountdown > 0;
    }

    /**
     * 現在のラウンド / マッチの勝者。マッチ確定なら {@code matchWinner}、ラウンド確定なら {@code roundWinner}。
     * 後方互換のため維持（GameRenderer の結果バナーで使用）。
     */
    public Winner getWinner() {
        return matchOver ? matchWinner : roundWinner;
    }

    /**
     * 決着理由。マッチ確定なら決着ラウンドの理由、ラウンド確定なら当ラウンドの理由。
     * 後方互換のため維持。
     */
    public FinishReason getReason() {
        return matchOver ? matchReason : roundReason;
    }

    /** マッチ全体の勝者（マッチ未確定は {@code NONE}）。 */
    public Winner getMatchWinner() {
        return matchWinner;
    }

    /** 直近ラウンドの勝者（ラウンド未決着は {@code NONE}）。 */
    public Winner getRoundWinner() {
        return roundWinner;
    }

    /** P1 の勝利ラウンド数。 */
    public int getP1Wins() {
        return p1Wins;
    }

    /** P2 の勝利ラウンド数。 */
    public int getP2Wins() {
        return p2Wins;
    }

    /** 現在のラウンド番号（1 始まり）。 */
    public int getCurrentRound() {
        return currentRound;
    }

    /** 先取勝利ラウンド数（例：2 = ベスト・オブ 3）。 */
    public int getRoundsToWin() {
        return roundsToWin;
    }

    /** ラウンド間カウントダウンの残フレーム数（0 の場合はカウントダウン中でない）。 */
    public int getBetweenCountdown() {
        return betweenCountdown;
    }

    /** 残り時間（秒, 切り上げ）。HUD タイマー表示に使用。 */
    public int getRemainingSeconds() {
        return (remainingFrames + GameConstants.TARGET_FPS - 1) / GameConstants.TARGET_FPS;
    }
}
