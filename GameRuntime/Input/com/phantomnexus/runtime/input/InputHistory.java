package com.phantomnexus.runtime.input;

/**
 * 1 プレイヤー分の入力履歴バッファ（Task 19: コマンド入力）。
 *
 * <p>毎フレームの「方向（テンキー表記 1〜9, 向き相対）」と「攻撃ボタンの立ち上がり」を固定長の
 * リングバッファに記録し、{@link CommandDetector} が波動拳・溜め・同時押しなどのコマンド成立を
 * 直近フレーム列から判定できるようにする。
 *
 * <p>テンキー表記は <b>向き相対</b>（前方 = 相手側）。{@code 5} = ニュートラル、{@code 6} = 前、{@code 4} = 後、
 * {@code 2} = 下、{@code 8} = 上、斜めは {@code 1/3/7/9}。値の算出は {@link #numpad}。
 */
public final class InputHistory {

    /** 履歴の保持フレーム数（コマンド検出の探索窓より十分大きく取る）。 */
    public static final int CAPACITY = 32;

    private final int[] dir = new int[CAPACITY];
    private final boolean[] attackEdge = new boolean[CAPACITY];
    private int head = -1; // 最新フレームのインデックス
    private int size;

    /**
     * このフレームの入力を記録する。
     *
     * @param numpadDir   テンキー方向（1〜9, 向き相対）
     * @param attackJustPressed 攻撃ボタンの立ち上がりがあったか
     */
    public void record(int numpadDir, boolean attackJustPressed) {
        head = (head + 1) % CAPACITY;
        dir[head] = numpadDir;
        attackEdge[head] = attackJustPressed;
        if (size < CAPACITY) {
            size++;
        }
    }

    /** {@code back} フレーム前（0 = 最新）の方向。範囲外は {@code 5}（ニュートラル）。 */
    public int dirAgo(int back) {
        if (back < 0 || back >= size) {
            return 5;
        }
        int idx = ((head - back) % CAPACITY + CAPACITY) % CAPACITY;
        return dir[idx];
    }

    /** {@code back} フレーム前に攻撃の立ち上がりがあったか。範囲外は false。 */
    public boolean attackEdgeAgo(int back) {
        if (back < 0 || back >= size) {
            return false;
        }
        int idx = ((head - back) % CAPACITY + CAPACITY) % CAPACITY;
        return attackEdge[idx];
    }

    /** 記録済みフレーム数（最大 {@link #CAPACITY}）。 */
    public int size() {
        return size;
    }

    /** 履歴をクリアする（ラウンド間リセット時などに使用）。 */
    public void reset() {
        head = -1;
        size = 0;
    }

    /**
     * 方向ボタンの押下状態を向き相対のテンキー方向（1〜9）に変換する。
     *
     * <p>前方は相手側（{@code facingRight} なら右が前）。式は {@code 5 + horiz + 3*vert}
     * （horiz: 後-1/中0/前+1、vert: 下-1/中0/上+1）。
     */
    public static int numpad(boolean left, boolean right, boolean up, boolean down, boolean facingRight) {
        boolean forward = facingRight ? right : left;
        boolean back = facingRight ? left : right;
        int horiz = forward ? 1 : (back ? -1 : 0);
        int vert = up ? 1 : (down ? -1 : 0);
        return 5 + horiz + 3 * vert;
    }
}
