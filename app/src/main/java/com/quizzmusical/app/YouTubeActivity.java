package com.quizzmusical.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;
import java.util.Locale;

/** Écoute d'une chanson dans l'application : flux audio direct, sans interface ni publicité YouTube. */
public class YouTubeActivity extends Activity {
    public static final String EXTRA_QUERY = "q";
    public static final String EXTRA_TITLE = "t";
    public static final String EXTRA_ARTIST = "a";

    private Ui ui;
    private ExoPlayer player;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status, timeCur, timeTot, playBtn;
    private SeekBar seek;
    private boolean userSeeking;
    private List<String> candidates;
    private int index;
    private boolean destroyed;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null && !userSeeking) {
                long d = player.getDuration();
                long p = player.getCurrentPosition();
                if (d > 0) {
                    seek.setMax((int) (d / 1000));
                    timeTot.setText(fmt(d));
                }
                seek.setProgress((int) (p / 1000));
                timeCur.setText(fmt(p));
            }
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        String q = getIntent().getStringExtra(EXTRA_QUERY);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String artist = getIntent().getStringExtra(EXTRA_ARTIST);
        if (q == null) q = "";

        FrameLayout root = new FrameLayout(this);
        root.addView(new MusicViews.Backdrop(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout l = ui.vbox();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24));
        sv.addView(l);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        LinearLayout card = ui.vbox();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(new MusicViews.Neon(ui.dp(24), ui.dp(2)));
        card.setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(26));
        ui.add(l, card, 20);

        MusicViews.Vinyl vinyl = new MusicViews.Vinyl(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ui.dp(150), ui.dp(150));
        vlp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(vinyl, vlp);
        card.addView(ui.center(title != null ? title : q, 24, Ui.TXT, true), ui.full(18));
        if (artist != null) card.addView(ui.center(artist, 17, Ui.MUTED, false), ui.full(4));
        status = ui.center("Recherche du titre…", 14, Ui.CYAN, false);
        card.addView(status, ui.full(14));

        seek = new SeekBar(this);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.NEON_PINK));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ORANGE));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser) timeCur.setText(fmt(p * 1000L)); }
            @Override public void onStartTrackingTouch(SeekBar s) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                userSeeking = false;
                if (player != null) player.seekTo(s.getProgress() * 1000L);
            }
        });
        card.addView(seek, ui.full(16));
        LinearLayout times = ui.hbox();
        timeCur = ui.text("0:00", 13, Ui.MUTED, false);
        timeTot = ui.text("--:--", 13, Ui.MUTED, false);
        timeTot.setGravity(Gravity.END);
        times.addView(timeCur, ui.weight(1, 0));
        times.addView(timeTot, ui.weight(1, 0));
        card.addView(times, ui.full(2));

        LinearLayout ctr = ui.hbox();
        TextView back = ui.button("⏪ 10 s", Ui.CARD2, Ui.TXT);
        playBtn = ui.button("▶", Ui.PINK, Ui.TXT);
        playBtn.setTextSize(24);
        TextView fwd = ui.button("10 s ⏩", Ui.CARD2, Ui.TXT);
        back.setTextSize(15);
        fwd.setTextSize(15);
        back.setOnClickListener(v -> { if (player != null) player.seekTo(Math.max(0, player.getCurrentPosition() - 10000)); });
        fwd.setOnClickListener(v -> { if (player != null) player.seekTo(player.getCurrentPosition() + 10000); });
        playBtn.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause(); else player.play();
        });
        ctr.addView(back, ui.weight(1, 4));
        ctr.addView(playBtn, ui.weight(1, 4));
        ctr.addView(fwd, ui.weight(1, 4));
        card.addView(ctr, ui.full(16));

        TextView other = ui.button("🔁 Autre version", Ui.BG, Ui.TXT);
        other.setTextSize(15);
        other.setOnClickListener(v -> playIndex(index + 1));
        ui.add(l, other, 18);
        TextView close = ui.button("Fermer", Ui.BG, Ui.MUTED);
        close.setOnClickListener(v -> finish());
        ui.add(l, close, 10);

        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) {
                playBtn.setText(playing ? "⏸" : "▶");
                if (playing) status.setText("♪ Lecture");
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) status.setText("Chargement…");
                else if (state == Player.STATE_ENDED) status.setText("Terminé");
            }
            @Override public void onPlayerError(PlaybackException error) {
                status.setText("Version illisible, essai suivant…");
                playIndex(index + 1);
            }
        });
        h.post(tick);

        YtAudio.search(q, new YtAudio.ListCb() {
            @Override public void ok(List<String> urls) {
                runOnUiThread(() -> { candidates = urls; playIndex(0); });
            }
            @Override public void err(String msg) {
                runOnUiThread(() -> status.setText("Introuvable : " + msg));
            }
        });
    }

    private void playIndex(int i) {
        if (destroyed || candidates == null || candidates.isEmpty()) return;
        if (i >= candidates.size()) {
            status.setText("Aucune autre version disponible");
            index = candidates.size() - 1;
            return;
        }
        index = i;
        status.setText(i == 0 ? "Préparation de l'écoute…" : "Version " + (i + 1) + "…");
        if (player != null) player.stop();
        YtAudio.resolve(candidates.get(i), new YtAudio.UrlCb() {
            @Override public void ok(String audioUrl) {
                runOnUiThread(() -> {
                    if (destroyed || player == null || index != i) return;
                    player.setMediaItem(MediaItem.fromUri(audioUrl));
                    player.prepare();
                    player.play();
                });
            }
            @Override public void err(String msg) {
                runOnUiThread(() -> { if (!destroyed && index == i) playIndex(i + 1); });
            }
        });
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.FRANCE, "%d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
