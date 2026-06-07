package com.phantomnexus.runtime.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

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
 * </ul>
 */
public final class ScreenshotController {

    /** 撮影フレームの既定値（描画が安定するまで少し待つ）。 */
    private static final int DEFAULT_FRAME = 90;

    private final String outputPath;
    private final int targetFrame;
    private int frameCount;
    private boolean done;

    /** システムプロパティから設定を読み取って構築する。撮影モード無効なら {@link #isEnabled()} が false。 */
    public ScreenshotController() {
        this.outputPath = trimToNull(System.getProperty("phantom.screenshot.path"));
        this.targetFrame = parsePositiveInt(System.getProperty("phantom.screenshot.frame"), DEFAULT_FRAME);
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
