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
    private final int maxRounds; // = 2 * roundsToWin - 1：引き分けを除いた決着ラウンド上限
    private final int totalFrames;
    private final int introFrames; // ラウンド開始イントロの総フレーム数（0 ならイントロ無し）

    // Win counters
    private int p1Wins = 0;
    private int p2Wins = 0;
    private int currentRound = 1;
    private int decisiveRounds = 0; // 引き分け以外（P1/P2 いずれかが勝利）のラウンド数

    // Current round state
    private int remainingFrames;
    private boolean roundFinished = false;
    private Winner roundWinner = Winner.NONE;
    private FinishReason roundReason = FinishReason.NONE;
    private boolean roundPerfect = false; // この決着ラウンドを勝者がノーダメージで取ったか（PERFECT 演出・Task 127）

    // Match state
    private boolean matchOver = false;
    private Winner matchWinner = Winner.NONE;
    private FinishReason matchReason = FinishReason.NONE;

    // Between-round state
    private int betweenCountdown = 0;
    private boolean nextRoundReady = false;

    // Round-intro state（"ROUND N" → "FIGHT!" 開始演出。この間は戦闘・タイマーを停止する）
    private int introCountdown;

    /** 既定（イントロ有り＝{@link GameConstants#ROUND_INTRO_FRAMES}）でラウンド管理を構築する。 */
    public RoundManager(BattleRules rules) {
        this(rules, GameConstants.ROUND_INTRO_FRAMES);
    }

    /**
     * イントロ長を指定してラウンド管理を構築する。
     *
     * @param rules 対戦ルール（制限時間・先取ラウンド数）
     * @param introFrames ラウンド開始イントロの総フレーム数（0 ならイントロをスキップ。撮影モードの後方互換用）
     */
    public RoundManager(BattleRules rules, int introFrames) {
        this.roundsToWin = rules.getRoundsToWin();
        this.maxRounds = 2 * roundsToWin - 1;
        this.totalFrames = Math.max(0, rules.getTimeLimitSeconds()) * GameConstants.TARGET_FPS;
        this.remainingFrames = totalFrames;
        this.introFrames = Math.max(0, introFrames);
        this.introCountdown = this.introFrames;
    }

    /**
     * 1 フレーム進める。ラウンド間カウントダウン中は戦闘を停止してカウントを減らし、
     * 0 に達したら次ラウンド準備フラグを立てる。アクティブラウンド中は KO と制限時間を判定する。
     */
    public void update(Fighter p1, Fighter p2) {
        if (matchOver) {
            return;
        }
        // ラウンド開始イントロ中は戦闘・タイマー・勝敗判定を止め、演出カウントのみ進める。
        if (introCountdown > 0) {
            introCountdown--;
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
            roundPerfect = computeRoundPerfect(p1, p2);
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
            roundPerfect = computeRoundPerfect(p1, p2);
            finishRound();
        }
    }

    private void finishRound() {
        if (roundWinner == Winner.P1) {
            p1Wins++;
            decisiveRounds++;
        } else if (roundWinner == Winner.P2) {
            p2Wins++;
            decisiveRounds++;
        }
        // 終了条件（引き分けは decisiveRounds に含めない）：
        //  1. いずれかが先取ラウンド数に到達
        //  2. 引き分け除く決着ラウンドが上限に到達（＝最多勝者が確定）
        //  3. 1 本勝負（maxRounds=1）で引き分け → 再戦なし
        boolean done = p1Wins >= roundsToWin
                || p2Wins >= roundsToWin
                || decisiveRounds >= maxRounds
                || (maxRounds == 1 && roundWinner == Winner.DRAW);
        if (done) {
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
        roundPerfect = false; // 新ラウンド開始で PERFECT 状態をクリア（Task 127）
        introCountdown = introFrames; // 新ラウンドも開始イントロを再生
    }

    /**
     * 決着したラウンドを勝者が**ノーダメージ**（HP 満タンのまま）で取ったか（PERFECT 演出・Task 127）。
     * 勝者の現在 HP が最大 HP と一致すれば true（chip を赤ゲージ回復で取り返した場合も満タンなら PERFECT）。
     * 引き分け / 未決着では false。乱数なし・HP の観測のみで決まる（リプレイと両立）。
     */
    private boolean computeRoundPerfect(Fighter p1, Fighter p2) {
        if (roundWinner == Winner.P1) {
            return p1.getCurrentHp() == p1.getMaxHp();
        }
        if (roundWinner == Winner.P2) {
            return p2.getCurrentHp() == p2.getMaxHp();
        }
        return false;
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

    /** ラウンド開始イントロ中か。この間は戦闘・タイマーを停止し、"ROUND N" / "FIGHT!" 演出を表示する。 */
    public boolean isRoundIntro() {
        return !matchOver && introCountdown > 0;
    }

    /**
     * イントロ中で末尾の "FIGHT!" 表示区間か（残り {@link GameConstants#FIGHT_FLASH_FRAMES} フレーム以下）。
     * false かつ {@link #isRoundIntro()} が true なら "ROUND N" 表示区間。
     */
    public boolean isFightFlash() {
        return isRoundIntro() && introCountdown <= GameConstants.FIGHT_FLASH_FRAMES;
    }

    /** ラウンド開始イントロの残フレーム数（0 はイントロ中でない）。 */
    public int getIntroCountdown() {
        return introCountdown;
    }

    /** ラウンド開始イントロの総フレーム数（0 ならイントロ無し）。ズーム演出の進捗計算等に使う（Task 138）。 */
    public int getIntroTotalFrames() {
        return introFrames;
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

    /** 直近に決着したラウンドを勝者がノーダメージで取ったか（PERFECT 演出・Task 127）。新ラウンド開始でクリア。 */
    public boolean isRoundPerfect() {
        return roundPerfect;
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
