package com.phantomnexus.runtime.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.phantomnexus.runtime.input.InputAction;
import com.phantomnexus.runtime.input.PlayerInput;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * ヘッドレス環境（Claude Code on the web / CI）での自動スクリーンショット撮影。
 *
 * <p>Windows ローカルでは {@code scripts/capture-app-window.ps1} で外部からウィンドウを撮るが、
 * ウィンドウシステムの無いリモート Linux では外部キャプチャが使えない。そこで本クラスは
 * <strong>アプリ自身が GL フレームバッファを PNG に書き出して自動終了する</strong>方式を提供する。
 * Xvfb（仮想ディスプレイ）＋ Mesa ソフトウェア GL の上で動かせば、ヘッドレスでも実画面どおりの
 * 絵が得られる（{@code scripts/capture-app-screenshot-linux.sh} 参照）。
 *
 * <p>システムプロパティで制御する（指定が無ければ通常起動と完全に同じ＝撮影モードは無効）。
 * <ul>
 *   <li>{@code phantom.screenshot.path} — 出力 PNG の絶対パス。指定時のみ撮影モード有効。</li>
 *   <li>{@code phantom.screenshot.frame} — 撮影するフレーム番号（既定 90 ≒ 1.5 秒@60fps）。
 *       初期化直後の未確定状態を避けるため数フレーム待ってから撮る。</li>
 *   <li>{@code phantom.screenshot.hold} — 起動時から押下状態に固定する入力（過渡状態の撮影用）。
 *       カンマ/空白区切りで {@code p1.up}・{@code p2.left}・{@code attack}（接頭辞省略時は p1）の形式。
 *       例：ジャンプ頂点を撮るなら {@code -Dphantom.screenshot.hold=p1.up} ＋ 頂点付近の {@code frame}。</li>
 *   <li>{@code phantom.screenshot.p1x} / {@code phantom.screenshot.p2x} — 初期中心 X のオーバーライド。
 *       近接が必要な過渡状態（被弾・接触マーカー等）を静止スクショで再現するために使う。</li>
 *   <li>{@code phantom.screenshot.timelimit} — ラウンド制限時間（秒）のオーバーライド。
 *       タイムアップ結果表示を短時間で撮るために使う。</li>
 *   <li>{@code phantom.screenshot.debug} — {@code true} でデバッグ当たり判定表示を起動時から ON。
 *       ヘッドレス撮影では F1 トグルを押せないための代替。</li>
 *   <li>{@code phantom.screenshot.script} — タイムド入力スクリプト（コマンド技の再現用）。
 *       書式 {@code start-end:tok+tok;...}。例：波動拳 {@code 1-12:p1.down;8-18:p1.down+p1.right;19-26:p1.right;22-22:p1.attack}。</li>
 *   <li>{@code phantom.screenshot.ai} — {@code false} で P2 の AI を無効化（人間=静止）。
 *       コマンド/飛び道具の撮影で P2 を動かしたくない時に使う。既定 ON。</li>
 *   <li>{@code phantom.screenshot.aidiff} — P2 AI 難易度（{@code easy} / {@code normal} / {@code hard}・Task 56）。
 *       他のプロパティと違い<b>撮影モードに依らず通常起動でも有効</b>（ゲームプレイ設定）。既定 HARD。
 *       生トークンを返し解決は呼び手（{@code AiController.Difficulty.fromToken}）が行う。</li>
 * </ul>
 */
public final class ScreenshotController {

    /** 撮影フレームの既定値（描画が安定するまで少し待つ）。 */
    private static final int DEFAULT_FRAME = 90;

    private final String outputPath;
    private final int targetFrame;
    private final EnumSet<InputAction> p1Hold;
    private final EnumSet<InputAction> p2Hold;
    private final Float p1SpawnX;
    private final Float p2SpawnX;
    private final Integer timeLimit;
    private final boolean debug;
    private final List<ScriptSegment> script = new ArrayList<>();
    private int scriptFrame;
    private int frameCount;
    private boolean done;

    /** タイムド入力スクリプトの 1 区間（{@code [start,end]} フレームで p1/p2 の押下を固定）。 */
    private static final class ScriptSegment {
        final int start;
        final int end;
        final EnumSet<InputAction> p1 = EnumSet.noneOf(InputAction.class);
        final EnumSet<InputAction> p2 = EnumSet.noneOf(InputAction.class);

        ScriptSegment(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /** システムプロパティから設定を読み取って構築する。撮影モード無効なら {@link #isEnabled()} が false。 */
    public ScreenshotController() {
        this.outputPath = trimToNull(System.getProperty("phantom.screenshot.path"));
        this.targetFrame = parsePositiveInt(System.getProperty("phantom.screenshot.frame"), DEFAULT_FRAME);
        this.p1Hold = EnumSet.noneOf(InputAction.class);
        this.p2Hold = EnumSet.noneOf(InputAction.class);
        // 初期 X オーバーライド（撮影モード時のみ）。近接が必要な過渡状態（被弾など）を再現するため。
        this.p1SpawnX = isEnabled() ? parseFloatOrNull(System.getProperty("phantom.screenshot.p1x")) : null;
        this.p2SpawnX = isEnabled() ? parseFloatOrNull(System.getProperty("phantom.screenshot.p2x")) : null;
        // 制限時間オーバーライド（撮影モード時のみ）。タイムアップ結果表示を短時間で撮るため。
        this.timeLimit = isEnabled() ? parsePositiveIntOrNull(System.getProperty("phantom.screenshot.timelimit")) : null;
        // デバッグ当たり判定表示の強制 ON（撮影モード時のみ）。F1 トグルの代替（ヘッドレス撮影用）。
        this.debug = isEnabled() && "true".equalsIgnoreCase(trimToNull(System.getProperty("phantom.screenshot.debug")));
        // hold は撮影モード（path 指定）時のみ解釈する。通常起動に hold だけ残っていても
        // プレイヤー入力を固定しない（撮影無効時は常に空集合）。
        if (isEnabled()) {
            parseHold(System.getProperty("phantom.screenshot.hold"));
            parseScript(System.getProperty("phantom.screenshot.script"));
        }
    }

    /**
     * タイムド入力スクリプトを解釈する。書式：{@code start-end:tok+tok;start-end:tok;...}
     * （例：波動拳 = {@code 1-12:p1.down;8-18:p1.down+p1.right;19-26:p1.right;22-22:p1.attack}）。
     * 区間は重ねてよく、各フレームで該当区間の押下の和集合を適用する。
     */
    private void parseScript(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return;
        }
        for (String seg : spec.split(";")) {
            if (seg.trim().isEmpty()) {
                continue;
            }
            int colon = seg.indexOf(':');
            if (colon < 0) {
                Gdx.app.log("Screenshot", "不正なスクリプト区間を無視: " + seg);
                continue;
            }
            String range = seg.substring(0, colon).trim();
            int dash = range.indexOf('-');
            int start = parseIntSafe(dash < 0 ? range : range.substring(0, dash), -1);
            int end = parseIntSafe(dash < 0 ? range : range.substring(dash + 1), start);
            if (start < 0) {
                Gdx.app.log("Screenshot", "不正なスクリプト範囲を無視: " + seg);
                continue;
            }
            ScriptSegment s = new ScriptSegment(start, end);
            addTokens(seg.substring(colon + 1), s.p1, s.p2);
            script.add(s);
        }
    }

    /** タイムド入力スクリプトが指定されているか。 */
    public boolean hasScript() {
        return !script.isEmpty();
    }

    /**
     * 現在のスクリプトフレームに応じた押下を p1/p2 へ適用し、内部フレームを 1 進める（撮影モード・毎フレーム呼ぶ）。
     * 描画ループの 1 フレームに 1 回呼ぶ前提（{@link #maybeCapture()} と同じ進行）。
     */
    public void applyTimedHolds(PlayerInput p1Input, PlayerInput p2Input) {
        if (script.isEmpty()) {
            return;
        }
        // -k の初期 hold をベースにスクリプト追加分をマージする（-k を上書きしない）。
        EnumSet<InputAction> p1 = p1Hold.isEmpty() ? EnumSet.noneOf(InputAction.class) : EnumSet.copyOf(p1Hold);
        EnumSet<InputAction> p2 = p2Hold.isEmpty() ? EnumSet.noneOf(InputAction.class) : EnumSet.copyOf(p2Hold);
        for (ScriptSegment s : script) {
            if (scriptFrame >= s.start && scriptFrame <= s.end) {
                p1.addAll(s.p1);
                p2.addAll(s.p2);
            }
        }
        p1Input.setForcedHold(p1);
        p2Input.setForcedHold(p2);
        scriptFrame++;
    }

    /**
     * 指定プレイヤー（1 / 2）で起動時から押下状態に固定するアクション集合を返す。
     * {@link com.phantomnexus.runtime.input.PlayerInput#setForcedHold} へそのまま渡す想定。
     */
    public EnumSet<InputAction> heldActions(int player) {
        return player == 2 ? p2Hold : p1Hold;
    }

    /**
     * 指定プレイヤーの初期中心 X（撮影用オーバーライド）。未指定 / 撮影無効時は {@code fallback} を返す。
     * 近接を要する過渡状態（被弾・接触マーカー等）を静止スクショで再現するために使う。
     */
    public float spawnX(int player, float fallback) {
        Float override = player == 2 ? p2SpawnX : p1SpawnX;
        return override != null ? override : fallback;
    }

    /** 制限時間（秒）の撮影用オーバーライド。未指定 / 撮影無効時は {@code fallback}。タイムアップ結果の撮影用。 */
    public int timeLimitSeconds(int fallback) {
        return timeLimit != null ? timeLimit : fallback;
    }

    /** デバッグ当たり判定表示を起動時から ON にするか（撮影モードの {@code debug=true} 指定時）。 */
    public boolean debugEnabled() {
        return debug;
    }

    /**
     * P2 の AI を有効にするか。撮影モードで {@code ai=false} 指定時のみ無効化（人間=静止）にできる。
     * 通常起動・未指定時は {@code fallback}（既定 ON）。コマンド/飛び道具の撮影で P2 を静止させたい時に使う。
     */
    public boolean aiEnabled(boolean fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        String v = trimToNull(System.getProperty("phantom.screenshot.ai"));
        return v == null ? fallback : !"false".equalsIgnoreCase(v);
    }

    /**
     * ラウンド開始イントロ（"ROUND N"/"FIGHT!" 演出・Task 42）を有効にするか。撮影モードでは既定で
     * <strong>スキップ</strong>（既存スクショレシピは frame1 から戦闘前提のため後方互換を保つ）、{@code intro=true}
     * 指定時のみ有効化して開始演出コマを撮れる。通常起動・撮影モード外では {@code fallback}（既定 ON）。
     */
    public boolean roundIntroEnabled(boolean fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        String v = trimToNull(System.getProperty("phantom.screenshot.intro"));
        return "true".equalsIgnoreCase(v);
    }

    /**
     * 読み込むステージ ID の撮影用オーバーライド。撮影モードで {@code stage=<id>} 指定時のみ差し替える。
     * 通常起動・未指定時は {@code fallback}。複数ステージ（背景）の見え方を 1 起動で撮り分けるために使う。
     */
    public String stageId(String fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        String v = trimToNull(System.getProperty("phantom.screenshot.stage"));
        return v != null ? v : fallback;
    }

    /**
     * 読み込むキャラクター ID の撮影用オーバーライド。撮影モードで {@code p1char=<id>} / {@code p2char=<id>}
     * 指定時のみ差し替える（{@code stageId} のキャラ版）。通常起動・未指定時は {@code fallback}。
     * 新キャラを 1 起動で撮るために使う（例：{@code -x p2char=fighter003}）。
     *
     * @param player 1（P1）または 2（P2）
     * @param fallback 未指定時に使う既定キャラ ID
     */
    public String charId(int player, String fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        String key = player == 2 ? "phantom.screenshot.p2char" : "phantom.screenshot.p1char";
        String v = trimToNull(System.getProperty(key));
        return v != null ? v : fallback;
    }

    /**
     * P2 の AI 難易度トークン（{@code easy} / {@code normal} / {@code hard}）のオーバーライド（Task 56）。
     * <b>撮影モードに依らず常に読む</b>点が他のオーバーライド（{@code charId}/{@code stageId} 等は撮影時のみ）と異なる
     * ——難易度は「撮影レシピ」ではなく<b>ゲームプレイ設定</b>で、通常起動（{@code gradle run -Dphantom.screenshot.aidiff=hard}）
     * でも効かせたいため（CodeRabbit 指摘）。未指定時は {@code fallback}（生トークンを返し、解決は呼び手＝Core が
     * {@code AiController.Difficulty.fromToken} で行う＝Debug→Battle 依存を作らない）。プロパティ名は転送リスト統一のため
     * {@code phantom.screenshot.} 名前空間のままだが、実体は撮影専用ではない（実行時メニュー化までの暫定設定窓口）。
     */
    public String aiDifficulty(String fallback) {
        String v = trimToNull(System.getProperty("phantom.screenshot.aidiff"));
        return v != null ? v : fallback;
    }

    /**
     * 指定プレイヤーの初期必殺技ゲージ量の撮影用オーバーライド（Task 44）。撮影モードで
     * {@code p1meter=<値>} / {@code p2meter=<値>} 指定時のみ返す（未指定 / 通常起動は {@code fallback}）。
     * EX 必殺技（メーター満タンで強化）の見え方を貯め直しなしで撮るために使う（例：{@code -x p1meter=100}）。
     */
    public float initialMeter(int player, float fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        String key = player == 2 ? "phantom.screenshot.p2meter" : "phantom.screenshot.p1meter";
        Float v = parseFloatOrNull(System.getProperty(key));
        return v != null ? v : fallback;
    }

    /**
     * トレーニングモード（Task 90）を起動時から有効にするか。撮影モードで {@code training=true} 指定時のみ。
     * 通常起動・未指定時は {@code fallback}（F4 トグルの初期値）。HP 無限のダミー相手でコンボ練習の見え方を撮る用。
     */
    public boolean trainingEnabled(boolean fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(trimToNull(System.getProperty("phantom.screenshot.training")));
    }

    /** {@code phantom.screenshot.hold} を解釈して p1/p2 の強制押下集合へ振り分ける。 */
    private void parseHold(String spec) {
        addTokens(spec, p1Hold, p2Hold);
    }

    /** トークン列（{@code p1.down}・{@code attack} 等をカンマ/空白/{@code +} 区切り）を p1/p2 集合へ振り分ける。 */
    private static void addTokens(String spec, EnumSet<InputAction> p1, EnumSet<InputAction> p2) {
        if (spec == null) {
            return;
        }
        for (String token : spec.split("[,+\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            EnumSet<InputAction> target = p1;
            String name = token;
            int sep = indexOfPrefixSeparator(token);
            if (sep >= 0) {
                String prefix = token.substring(0, sep).toLowerCase();
                name = token.substring(sep + 1);
                if (prefix.equals("p2")) {
                    target = p2;
                }
            }
            InputAction action = toAction(name);
            if (action != null) {
                target.add(action);
            } else {
                Gdx.app.log("Screenshot", "未知の入力トークンを無視: " + token);
            }
        }
    }

    private static int indexOfPrefixSeparator(String token) {
        int dot = token.indexOf('.');
        int colon = token.indexOf(':');
        if (dot < 0) {
            return colon;
        }
        if (colon < 0) {
            return dot;
        }
        return Math.min(dot, colon);
    }

    private static InputAction toAction(String name) {
        String upper = name.trim().toUpperCase();
        // 後方互換：旧 "attack" トークン（スクリプト / CLAUDE.md の例で使用）を ATTACK_LIGHT として解釈。
        if (upper.equals("ATTACK")) {
            return InputAction.ATTACK_LIGHT;
        }
        try {
            return InputAction.valueOf(upper);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 撮影モードが有効か（出力パス指定があるか）。 */
    public boolean isEnabled() {
        return outputPath != null;
    }

    /**
     * 1 フレーム描画し終えた直後に呼ぶ。撮影モードが有効で目標フレームに達したら、
     * フレームバッファを PNG 保存してアプリを終了させる。それ以外は何もしない。
     */
    public void maybeCapture() {
        if (!isEnabled() || done) {
            return;
        }
        frameCount++;
        if (frameCount < targetFrame) {
            return;
        }
        capture();
        done = true;
        // 撮影が済んだらゲームループを終了（JVM もそのまま終了する）。
        Gdx.app.exit();
    }

    /** 実バックバッファ全体を取得し、PNG（上下反転補正あり）で保存する。 */
    private void capture() {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
        try {
            FileHandle file = Gdx.files.absolute(outputPath);
            // GL のフレームバッファは原点が左下のため flipY=true で上下を補正して書き出す。
            PixmapIO.writePNG(file, pixmap, java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
            Gdx.app.log("Screenshot", "保存しました: " + file.path() + " (" + width + "x" + height + ")");
        } finally {
            pixmap.dispose();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Float parseFloatOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parsePositiveIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseIntSafe(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
