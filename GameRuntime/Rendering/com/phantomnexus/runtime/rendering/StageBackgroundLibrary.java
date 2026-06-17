package com.phantomnexus.runtime.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.Map;

/**
 * ステージの全画面背景 PNG の読み込み・キャッシュ（外部デザイン取り込み用）。
 *
 * <p>{@link com.phantomnexus.shared.types.Stage#getBackground()} のパス（クラスパス相対 PNG）を
 * {@link Texture} として 1 度だけ読み込み、{@link TextureRegion}（正立）にして返す。キャラの
 * {@link SpriteLibrary} と同方針で、データ（パス）の単一の真実は {@code Shared/Types.Stage} 側にあり、
 * 本クラスは GPU リソース（テクスチャ）だけを担う。
 *
 * <p>パス未指定・PNG 欠落・読み込み失敗時は {@code null} を返し、呼び出し側（{@code GameRenderer}）は
 * 従来の手続き背景（空グラデ＋多層シルエット＋地面）へフォールバックする。失敗したパスもキャッシュして
 * 毎フレームのディスクアクセス・例外を避ける。
 */
public class StageBackgroundLibrary {

    /** パス → 正立 TextureRegion（読み込み失敗時は null をキャッシュ）。 */
    private final Map<String, TextureRegion> byPath = new HashMap<>();

    /**
     * 指定パスの背景を（未読込なら）読み込んで返す。テクスチャ生成は GL コンテキストを要するため
     * 描画スレッドから呼ぶこと。パス {@code null}／欠落／失敗時は {@code null} を返す（矩形＝手続き
     * 背景フォールバック）。読み込み済み（成功・失敗とも）はキャッシュから即返す。
     */
    public TextureRegion get(String path) {
        if (path == null) {
            return null;
        }
        if (byPath.containsKey(path)) {
            return byPath.get(path);
        }
        TextureRegion region = load(path);
        byPath.put(path, region); // 失敗（null）もキャッシュして再試行しない
        return region;
    }

    private TextureRegion load(String path) {
        FileHandle file = Gdx.files.classpath(path);
        if (!file.exists()) {
            Gdx.app.log("StageBackgroundLibrary", "背景 PNG が見つかりません（手続き背景にフォールバック）: " + path);
            return null;
        }
        try {
            Texture texture = new Texture(file);
            // 全画面に引き伸ばすため滑らかな線形フィルタにする（ドット絵キャラとは別＝背景は写実的な拡大）。
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            // TextureRegion でラップすると batch.draw が正立で描く（Texture 直 draw の上下反転を避ける）。
            return new TextureRegion(texture);
        } catch (RuntimeException e) {
            Gdx.app.log("StageBackgroundLibrary", "背景 PNG の読み込みに失敗（手続き背景にフォールバック）: " + path
                    + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /** 読み込んだ全テクスチャを破棄する（アプリ終了時）。 */
    public void dispose() {
        for (TextureRegion region : byPath.values()) {
            if (region != null && region.getTexture() != null) {
                region.getTexture().dispose();
            }
        }
        byPath.clear();
    }
}
