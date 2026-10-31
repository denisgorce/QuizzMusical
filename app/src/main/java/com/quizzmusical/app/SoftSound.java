package com.quizzmusical.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sons doux synthétisés (carillons sinus à décroissance), à la place des bips téléphoniques. */
public final class SoftSound {
    private static final int SR = 22050;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private volatile short[] tick, click, endTurn, endGame;

    public SoftSound() {
        ex.execute(() -> {
            click = synth(new double[]{1046.5}, new double[]{0}, 0.06, 0.10, 30);
            tick = synth(new double[]{880}, new double[]{0}, 0.35, 0.22, 9);
            // Fin de passage : carillon descendant mi - do - la
            endTurn = synth(new double[]{659.25, 523.25, 440.0}, new double[]{0, 0.22, 0.44}, 1.8, 0.26, 3.2);
            // Fin de partie : arpège montant do - mi - sol - do
            endGame = synth(new double[]{523.25, 659.25, 783.99, 1046.5}, new double[]{0, 0.15, 0.30, 0.45}, 2.2, 0.24, 2.6);
        });
    }

    public void click() { play(click); }
    public void tick() { play(tick); }
    public void endTurn() { play(endTurn); }
    public void endGame() { play(endGame); }

    /** Notes sinus (+ harmonique douce), attaque courte et décroissance exponentielle. */
    private static short[] synth(double[] freqs, double[] starts, double dur, double vol, double decay) {
        int n = (int) (SR * dur);
        double[] buf = new double[n];
        for (int i = 0; i < freqs.length; i++) {
            int s0 = (int) (starts[i] * SR);
            for (int k = s0; k < n; k++) {
                double t = (k - s0) / (double) SR;
                double env = t < 0.012 ? t / 0.012 : Math.exp(-(t - 0.012) * decay);
                double w = 2 * Math.PI * freqs[i] * t;
                buf[k] += vol * env * (Math.sin(w) + 0.18 * Math.sin(2 * w) + 0.06 * Math.sin(3 * w));
            }
        }
        short[] out = new short[n];
        int fade = Math.min(n, SR / 40);
        for (int k = 0; k < n; k++) {
            double v = buf[k];
            if (k > n - fade) v *= (n - k) / (double) fade;
            v = Math.max(-1, Math.min(1, v));
            out[k] = (short) (v * 32000);
        }
        return out;
    }

    private void play(short[] pcm) {
        if (pcm == null) return;
        ex.execute(() -> {
            try {
                final AudioTrack at = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SR)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                at.write(pcm, 0, pcm.length);
                at.play();
                final long ms = pcm.length * 1000L / SR + 200;
                new Thread(() -> {
                    try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
                    try { at.release(); } catch (Exception ignored) { }
                }).start();
            } catch (Exception ignored) { /* son facultatif */ }
        });
    }

    public void shutdown() { ex.shutdown(); }
}
