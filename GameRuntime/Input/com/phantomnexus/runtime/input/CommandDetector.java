package com.phantomnexus.runtime.input;

/**
 * 入力履歴からコマンド成立を判定する（Task 19）。
 *
 * <p>判定は「攻撃ボタンの立ち上がりフレーム」を起点に、直近の方向列（{@link InputHistory}, 向き相対の
 * テンキー表記）を遡って評価する。MVP は次の 3 種を検出する（[docs/DataFormat.md](../../../../../../docs/DataFormat.md)）：
 * <ul>
 *   <li><b>波動拳</b>：下(2) → 前(6) を含む下→前の流れ＋攻撃（QCF, 236+A）。</li>
 *   <li><b>溜め</b>：後(4)を {@link #CHARGE_FRAMES} 以上溜めてから前(6)＋攻撃。</li>
 *   <li><b>同時押し</b>：下(2)を保持したまま攻撃（DOWN+A）。</li>
 * </ul>
 * 上位（波動拳 → 溜め → 同時押し）から評価し、最初に成立したものを返す。攻撃立ち上がりが無ければ
 * {@link Command#NONE}。本クラスは状態を持たない純判定。
 */
public final class CommandDetector {

    /** コマンド方向入力を遡って探索する窓（フレーム）。 */
    private static final int MOTION_WINDOW = 14;
    /** スーパー必殺技（236236＝波動拳 2 回）を遡って探索する窓（フレーム・Task 108）。2 モーション分なので広め。 */
    private static final int SUPER_MOTION_WINDOW = 28;
    /** 溜め成立に必要な「後」入力の最小保持フレーム。 */
    public static final int CHARGE_FRAMES = 30;
    /** 溜め解放（前+攻撃）を受け付ける、溜め終了からの猶予フレーム。 */
    private static final int CHARGE_RELEASE_WINDOW = 12;

    private CommandDetector() {
        // ユーティリティ（インスタンス化禁止）
    }

    /** 直近フレームのコマンド成立を判定する。攻撃立ち上がりが起点。 */
    public static Command detect(InputHistory h) {
        if (!h.attackEdgeAgo(0)) {
            return Command.NONE;
        }
        // スーパー（236236）は波動拳（236）を内包するため、より厳しいこちらを先に評価する（Task 108）。
        if (isSuper(h)) {
            return Command.SUPER;
        }
        if (isHadouken(h)) {
            return Command.HADOUKEN;
        }
        if (isCharge(h)) {
            return Command.CHARGE_SHOT;
        }
        if (isForwardless2(h)) {
            return Command.DOWN_ATTACK;
        }
        return Command.NONE;
    }

    /** 波動拳：攻撃時に前(6/3)で、窓内に下(2)→前(6) の順序がある。 */
    private static boolean isHadouken(InputHistory h) {
        int now = h.dirAgo(0);
        if (now != 6 && now != 3) {
            return false;
        }
        // 窓内で「前(6)」の直近出現を探し、その前に「下(2)」があるか。
        int forwardAt = -1;
        for (int back = 0; back < MOTION_WINDOW; back++) {
            if (h.dirAgo(back) == 6) {
                forwardAt = back;
                break;
            }
        }
        if (forwardAt < 0) {
            return false;
        }
        for (int back = forwardAt; back < MOTION_WINDOW; back++) {
            if (h.dirAgo(back) == 2 || h.dirAgo(back) == 1 || h.dirAgo(back) == 3) {
                // 下要素（2/1/3）を前(6)より過去に確認 → QCF 成立。
                if (back > forwardAt) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * スーパー（236236＋攻撃・Task 108）：攻撃時に前(6/3)で、窓内に「下→前」の波動拳要素が <b>2 回</b> 遡って見つかる。
     * 新しい順に走査し、前(6)＋その直後（過去側）の下(2/1/3)を 1 モーションと数え、2 モーション以上で成立。
     * 純判定（履歴のみ）で乱数なし。
     */
    private static boolean isSuper(InputHistory h) {
        int now = h.dirAgo(0);
        if (now != 6 && now != 3) {
            return false;
        }
        int motions = 0;
        int back = 0;
        while (back < SUPER_MOTION_WINDOW && back < InputHistory.CAPACITY) {
            if (h.dirAgo(back) == 6) {
                // この前(6)より過去側（数フレーム以内）に下要素があれば 1 モーション成立。
                int downAt = -1;
                for (int b2 = back + 1; b2 <= back + 8 && b2 < SUPER_MOTION_WINDOW && b2 < InputHistory.CAPACITY; b2++) {
                    int d = h.dirAgo(b2);
                    if (d == 2 || d == 1 || d == 3) {
                        downAt = b2;
                        break;
                    }
                }
                if (downAt >= 0) {
                    motions++;
                    if (motions >= 2) {
                        return true;
                    }
                    back = downAt + 1; // この下より過去から次のモーションを探す
                    continue;
                }
            }
            back++;
        }
        return false;
    }

    /** 溜め：攻撃時に前(6)で、過去に後(4)を CHARGE_FRAMES 連続保持していた。 */
    private static boolean isCharge(InputHistory h) {
        if (h.dirAgo(0) != 6) {
            return false;
        }
        // 直近の解放窓内に後(4)保持区間が CHARGE_FRAMES 続いていたか。
        for (int releaseBack = 0; releaseBack <= CHARGE_RELEASE_WINDOW; releaseBack++) {
            int start = releaseBack;
            int held = 0;
            for (int back = start; back < start + CHARGE_FRAMES + CHARGE_RELEASE_WINDOW && back < InputHistory.CAPACITY; back++) {
                if (h.dirAgo(back) == 4 || h.dirAgo(back) == 1 || h.dirAgo(back) == 7) {
                    held++;
                    if (held >= CHARGE_FRAMES) {
                        return true;
                    }
                } else {
                    held = 0;
                }
            }
        }
        return false;
    }

    /** 同時押し：攻撃時に下(2/1/3)を保持。 */
    private static boolean isForwardless2(InputHistory h) {
        int now = h.dirAgo(0);
        return now == 2 || now == 1 || now == 3;
    }
}
