package com.phantomnexus.runtime.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;

/**
 * BGM（バックグラウンドミュージック）の読み込みとループ再生を管理する。
 *
 * <p>Assets/Music/ 以下の WAV ファイルを遅延ロードし、
 * {@link #playBattle()} / {@link #playMenu()} でシーンに合った BGM を切り替える。
 * ロード失敗（ヘッドレス環境・ファイル欠落）は警告のみで続行し、
 * 以降の呼び出しは静かに無視される（null-safe）。
 *
 * <p>M キーで {@link SoundManager} と連動してトグル可能（{@link #setEnabled(boolean)}）。
 */
public class MusicManager {

    private Music current;
    private Music battle;
    private Music menu;

    private boolean enabled = true;
    private String currentTrack = "";

    public MusicManager() {
        battle = load("Music/battle.wav");
        menu   = load("Music/menu.wav");
        if (battle != null) battle.setLooping(true);
        if (menu   != null) menu.setLooping(true);
    }

    private static Music load(String path) {
        try {
            return Gdx.audio.newMusic(Gdx.files.classpath(path));
        } catch (Exception e) {
            Gdx.app.log("MusicManager", "BGM ロード失敗（無音で続行）: " + path);
            return null;
        }
    }

    /** バトル BGM を再生する（既に再生中なら何もしない）。 */
    public void playBattle() {
        play(battle, "battle", 0.5f);
    }

    /** メニュー BGM を再生する（既に再生中なら何もしない）。 */
    public void playMenu() {
        play(menu, "menu", 0.6f);
    }

    /** 現在の BGM を停止する。 */
    public void stop() {
        if (current != null) {
            current.stop();
        }
        currentTrack = "";
    }

    /**
     * SE と連動して BGM を有効/無効化する。
     * 無効化時は一時停止し、有効化時は再開する。
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (current == null) return;
        if (enabled) {
            current.play();
        } else {
            current.pause();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 読み込んだ全 BGM を破棄する（アプリ終了時）。 */
    public void dispose() {
        if (battle != null) battle.dispose();
        if (menu   != null) menu.dispose();
        battle = null;
        menu   = null;
        current = null;
    }

    private void play(Music track, String name, float volume) {
        if (name.equals(currentTrack)) return;
        if (current != null) current.stop();
        current = track;
        currentTrack = name;
        if (current == null) return;
        current.setVolume(volume);
        if (enabled) current.play();
    }
}
