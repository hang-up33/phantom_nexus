package com.phantomnexus.runtime.debug;

import com.badlogic.gdx.Gdx;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 入力リプレイ（記録 / 再生）の開発ツール。{@link ScreenshotController} と同じくシステムプロパティ駆動で、
 * 指定が無ければ通常起動と完全に同じ（リプレイモードは無効）。
 *
 * <p><strong>原理</strong>：本エンジンのシミュレーションは「1 render = 1 固定ステップ」で dt 非依存、
 * AI も乱数を持たない決定的処理のため、<em>毎フレームの入力さえ記録すれば同じ試合を完全に再現できる</em>
 * （ゲーム状態を丸ごと保存する必要がない）。本クラスは各フレームの押下状態を 1 行ずつ記録し、再生時は
 * その押下集合を {@link PlayerInput#setForcedHold} で注入し直すことで、撮影と同じ仕組みでリプレイを実現する。
 *
 * <p><strong>記録形式</strong>（テキスト）：先頭にヘッダ {@code PHANTOM_REPLAY v1}、以降 1 行 1 フレームで
 * {@code p1mask,p2mask,ai}。{@code p1mask}/{@code p2mask} は各プレイヤーの押下アクションを
 * {@link InputAction#ordinal()} のビット位置で畳んだ整数、{@code ai} は当該フレームで P2 が AI 制御だったか
 * （1=AI, 0=人間）。AI 制御フレームの {@code p2mask} は 0（再生側は AI を走らせるため不要）。
 * <em>注意：{@link InputAction} の列挙順を変更すると既存ログのマスク解釈がずれる。</em>
 *
 * <p><strong>システムプロパティ</strong>：
 * <ul>
 *   <li>{@code phantom.replay.record} — 記録先パス。指定時のみ記録モード有効（毎フレーム追記＋flush）。</li>
 *   <li>{@code phantom.replay.play} — 再生元パス。指定時のみ再生モード有効（記録入力を注入）。</li>
 *   <li>{@code phantom.replay.ai} — {@code false} で記録開始時から P2 AI を OFF（静止相手に対して記録したい時）。
 *       開始後も F2 でトグル可能で、その切替もフレーム単位で記録・再生される。</li>
 * </ul>
 */
public final class ReplayController {

    private final boolean recording;
    private final boolean replaying;

    // 記録モード
    private BufferedWriter writer;

    // 再生モード（各要素 {p1mask, p2mask, ai}）
    private List<int[]> frames;
    private int frameIndex;
    private boolean currentAi = true;

    /** システムプロパティを読み取り、記録 / 再生の準備を行う。記録・再生いずれも未指定なら何もしない。 */
    public ReplayController() {
        String recordPath = trimToNull(System.getProperty("phantom.replay.record"));
        String playPath = trimToNull(System.getProperty("phantom.replay.play"));
        boolean rec = recordPath != null;
        boolean play = playPath != null;
        if (rec) {
            try {
                // 親ディレクトリが無いと FileWriter が落ちて記録が無効化されるため、先に作る。
                java.io.File parent = new java.io.File(recordPath).getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                writer = new BufferedWriter(new FileWriter(recordPath));
                writer.write("PHANTOM_REPLAY v1");
                writer.newLine();
            } catch (Exception e) {
                Gdx.app.error("Replay", "記録ファイルを開けません: " + recordPath, e);
                rec = false;
            }
        }
        if (play) {
            frames = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new FileReader(playPath))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int[] parsed = parseLine(line);
                    if (parsed != null) {
                        frames.add(parsed);
                    }
                }
            } catch (Exception e) {
                Gdx.app.error("Replay", "再生ファイルを読めません: " + playPath, e);
                play = false;
                frames = null;
            }
        }
        this.recording = rec;
        this.replaying = play;
    }

    /** 記録モードが有効か。 */
    public boolean isRecording() {
        return recording;
    }

    /** 再生モードが有効か。 */
    public boolean isReplaying() {
        return replaying;
    }

    /** 再生に読み込んだ総フレーム数（HUD 表示用）。再生モード以外は 0。 */
    public int frameCount() {
        return frames != null ? frames.size() : 0;
    }

    /**
     * 記録開始時の P2 AI 状態。{@code phantom.replay.ai=false} 指定時のみ {@code false}、未指定は {@code fallback}。
     * 静止した相手に対して記録したい時に使う（開始後も F2 でトグル可能）。
     */
    public boolean startAiEnabled(boolean fallback) {
        String v = trimToNull(System.getProperty("phantom.replay.ai"));
        if (v == null) {
            return fallback;
        }
        return !"false".equalsIgnoreCase(v);
    }

    /**
     * 再生モード：現在フレームの記録入力を {@code p1}/{@code p2} の押下として注入し、内部フレームを 1 進める。
     * P2 が AI 制御だったフレームは {@link #replayAi(boolean)} が {@code true} を返すので、呼び出し側は AI を走らせる。
     * 描画ループの 1 フレームに 1 回・update より前に呼ぶ前提。
     */
    public void applyReplayFrame(PlayerInput p1, PlayerInput p2) {
        if (!replaying) {
            return;
        }
        if (frameIndex < frames.size()) {
            int[] f = frames.get(frameIndex);
            p1.setForcedHold(fromMask(f[0]));
            p2.setForcedHold(fromMask(f[1]));
            currentAi = f[2] != 0;
        } else {
            // ログ終端以降は入力を解除して静止（試合は通常ここで決着済み）。AI 状態は直前を維持。
            p1.setForcedHold(EnumSet.noneOf(InputAction.class));
            p2.setForcedHold(EnumSet.noneOf(InputAction.class));
        }
        frameIndex++;
    }

    /** 直近に {@link #applyReplayFrame} で適用したフレームの P2 AI 状態。再生モード以外は {@code fallback}。 */
    public boolean replayAi(boolean fallback) {
        return replaying ? currentAi : fallback;
    }

    /**
     * 記録モード：このフレームの P1/P2 押下状態と P2 AI 状態を 1 行追記する。
     * ウィンドウの強制終了でもログを失わないよう毎フレーム flush する。update より前に呼ぶ前提。
     */
    public void recordFrame(PlayerInput p1, PlayerInput p2, boolean p2AiEnabled) {
        if (!recording || writer == null) {
            return;
        }
        try {
            int p1Mask = toMask(p1);
            // AI 制御フレームの P2 入力は再生側で使わないため 0 を記録する。
            int p2Mask = p2AiEnabled ? 0 : toMask(p2);
            writer.write(p1Mask + "," + p2Mask + "," + (p2AiEnabled ? 1 : 0));
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            Gdx.app.error("Replay", "記録の書き込みに失敗", e);
        }
    }

    /** 記録ファイルを閉じる（アプリ終了時に呼ぶ）。記録は毎フレーム flush 済みのため失敗は無視してよい。 */
    public void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (Exception ignored) {
                // 既に flush 済み。
            }
            writer = null;
        }
    }

    /** {@link InputAction} の押下状態を ordinal ビットのマスクへ畳む。 */
    private static int toMask(PlayerInput in) {
        int mask = 0;
        for (InputAction a : InputAction.values()) {
            if (in.isDown(a)) {
                mask |= 1 << a.ordinal();
            }
        }
        return mask;
    }

    /** マスクを {@link InputAction} 集合へ展開する（{@link PlayerInput#setForcedHold} 用）。 */
    private static EnumSet<InputAction> fromMask(int mask) {
        EnumSet<InputAction> set = EnumSet.noneOf(InputAction.class);
        for (InputAction a : InputAction.values()) {
            if ((mask & (1 << a.ordinal())) != 0) {
                set.add(a);
            }
        }
        return set;
    }

    /**
     * 記録 1 行を {@code {p1mask, p2mask, ai}} へ解釈する。データ行（先頭が数字）以外（ヘッダ・空行）は {@code null}。
     * {@code ai} 欠落時は 1（AI）とみなす。
     */
    private static int[] parseLine(String raw) {
        if (raw == null) {
            return null;
        }
        String line = raw.trim();
        if (line.isEmpty() || line.charAt(0) < '0' || line.charAt(0) > '9') {
            return null;
        }
        String[] parts = line.split(",");
        try {
            int p1 = Integer.parseInt(parts[0].trim());
            int p2 = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            int ai = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1;
            return new int[]{p1, p2, ai};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
