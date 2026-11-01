package com.quizzmusical.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lecture masquée de l'original pour le blind test (flux audio direct, sans interface YouTube). */
public final class BlindAudio {

    public interface Listener {
        void onStarted();
        void onFailed();
    }

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, String> cache = new HashMap<>();
    private ExoPlayer player;
    private Listener listener;
    private List<String> candidates;
    private int index;
    private int token; // invalide les réponses asynchrones d'une chanson précédente
    private boolean middle, seeked, started;
    private Song song;

    public BlindAudio(Context ctx) { this.ctx = ctx; }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(ctx).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && middle && !seeked) {
                    seeked = true;
                    long d = player.getDuration();
                    if (d > 90000) player.seekTo(Math.min(d * 35 / 100, 70000));
                }
            }
            @Override public void onIsPlayingChanged(boolean playing) {
                if (playing && !started) {
                    started = true;
                    if (listener != null) listener.onStarted();
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (candidates == null && song != null) search(token, song); // flux préchargé expiré : nouvelle recherche
                else tryCandidate(token, index + 1);
            }
        });
    }

    /** Lance l'extrait de la chanson (recherche, extraction du flux, positionnement). */
    public void play(Song s, boolean fromMiddle, Listener l) {
        ensurePlayer();
        final int t = ++token;
        listener = l;
        middle = fromMiddle;
        seeked = false;
        started = false;
        song = s;
        candidates = null;
        player.stop();
        String cached = cache.remove(s.id);
        if (cached != null) {
            start(cached);
            return;
        }
        search(t, s);
    }

    private void search(final int t, Song s) {
        YtAudio.search(s.query(), new YtAudio.ListCb() {
            @Override public void ok(List<String> urls) {
                h.post(() -> {
                    if (t != token) return;
                    candidates = urls;
                    tryCandidate(t, 0);
                });
            }
            @Override public void err(String msg) {
                h.post(() -> { if (t == token && listener != null) listener.onFailed(); });
            }
        });
    }

    private void tryCandidate(final int t, final int i) {
        if (t != token) return;
        if (candidates == null || i >= candidates.size()) {
            if (listener != null) listener.onFailed();
            return;
        }
        index = i;
        YtAudio.resolve(candidates.get(i), new YtAudio.UrlCb() {
            @Override public void ok(String audioUrl) { h.post(() -> { if (t == token) start(audioUrl); }); }
            @Override public void err(String msg) { h.post(() -> tryCandidate(t, i + 1)); }
        });
    }

    private void start(String url) {
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }

    /** Prépare en avance le flux de la chanson suivante. */
    public void prefetch(final Song s) {
        if (cache.containsKey(s.id)) return;
        YtAudio.search(s.query(), new YtAudio.ListCb() {
            @Override public void ok(List<String> urls) {
                YtAudio.resolve(urls.get(0), new YtAudio.UrlCb() {
                    @Override public void ok(String audioUrl) { h.post(() -> cache.put(s.id, audioUrl)); }
                    @Override public void err(String msg) { }
                });
            }
            @Override public void err(String msg) { }
        });
    }

    public void pause() { if (player != null) player.pause(); }

    public void resume() { if (player != null) player.play(); }

    public void stop() {
        token++;
        if (player != null) player.stop();
    }

    public void release() {
        token++;
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
