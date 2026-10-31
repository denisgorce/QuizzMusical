package com.quizzmusical.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int[] SECONDS = {30, 45, 60, 75, 90, 120};
    private static final int[] PENALTIES = {0, 3, 5, 10};
    private static final String[] DIFFS = {"Mix", "Incontournables", "Connaisseurs"};

    private static final int SC_HOME = 0, SC_SETUP = 1, SC_NAMES = 2, SC_GAME = 3, SC_PLAY = 4, SC_FINAL = 5;

    private Ui ui;
    private FrameLayout root;
    private SharedPreferences prefs;
    private List<Song> catalog = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private SoftSound sound;
    private Vibrator vib;
    private int screen = SC_HOME;

    // Réglages
    private int modeIdx, playersN, teamsN, secIdx, roundsN, penIdx, diffIdx;
    private boolean challengesOn, bonusOn, listenOnPass;
    private List<EditText> nameFields = new ArrayList<>();

    // Partie
    private GameEngine game;
    private String currentStyle;
    private long remainingMs, songElapsedMs, lastTick, totalMs;
    private boolean running;
    private int lastBeepSec;
    private TextView timerText, foundText, scoreText;
    private ProgressBar progress;
    private LinearLayout songCard;
    private View playRoot;
    private View overlay;
    private MusicViews.Backdrop backdrop;
    private boolean stoppedEarly;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            long dt = now - lastTick;
            lastTick = now;
            remainingMs -= dt;
            songElapsedMs += dt;
            updateTimerUi();
            int sec = (int) Math.ceil(remainingMs / 1000.0);
            if (remainingMs <= 5000 && sec > 0 && sec != lastBeepSec) {
                lastBeepSec = sec;
                beep();
            }
            if (remainingMs <= 0) { timeUp(); return; }
            h.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        prefs = getSharedPreferences("quizz", MODE_PRIVATE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        backdrop = new MusicViews.Backdrop(this);
        setContentView(root);
        loadCatalog();
        loadSettings();
        sound = new SoftSound();
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        showHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) {
            pauseTimer();
            showPauseOverlay();
        }
        savePlayed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        sound.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (overlay != null && screen == SC_PLAY && running) return;
        switch (screen) {
            case SC_HOME: super.onBackPressed(); break;
            case SC_SETUP: showHome(); break;
            case SC_NAMES: showSetup(); break;
            case SC_FINAL: showHome(); break;
            default: confirmQuit(); break;
        }
    }

    // ---------- Données ----------

    private void loadCatalog() {
        List<String> lines = new ArrayList<>();
        for (String f : new String[]{"songs.txt", "songs_monde.txt", "songs_ru.txt", "songs_en.txt", "songs_classique.txt", "songs_inter.txt"}) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(f), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) lines.add(l);
            } catch (Exception e) {
                Toast.makeText(this, "Catalogue introuvable : " + f, Toast.LENGTH_LONG).show();
            }
        }
        catalog = Song.merge(Song.parseAll(lines));
    }

    private void loadSettings() {
        modeIdx = prefs.getInt("mode", 0);
        playersN = prefs.getInt("players", 4);
        teamsN = prefs.getInt("teams", 2);
        secIdx = prefs.getInt("sec", 2);
        roundsN = prefs.getInt("rounds", 3);
        penIdx = prefs.getInt("pen", 2);
        diffIdx = prefs.getInt("diff", 0);
        challengesOn = prefs.getBoolean("chal", true);
        bonusOn = prefs.getBoolean("bonus", true);
        listenOnPass = prefs.getBoolean("listen", true);
    }

    private void saveSettings() {
        prefs.edit().putInt("mode", modeIdx).putInt("players", playersN).putInt("teams", teamsN)
                .putInt("sec", secIdx).putInt("rounds", roundsN).putInt("pen", penIdx).putInt("diff", diffIdx)
                .putBoolean("chal", challengesOn).putBoolean("bonus", bonusOn).putBoolean("listen", listenOnPass).apply();
    }

    private Set<String> loadPlayed() {
        return new HashSet<>(prefs.getStringSet("played", new HashSet<String>()));
    }

    private void savePlayed() {
        if (game != null) prefs.edit().putStringSet("played", new HashSet<>(game.playedSet())).apply();
    }

    // ---------- Structure d'écran ----------

    private LinearLayout page() {
        h.removeCallbacksAndMessages(null);
        running = false;
        overlay = null;
        root.removeAllViews();
        root.setBackgroundColor(Ui.BG);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        backdrop.setAlert(false);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout l = ui.vbox();
        l.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(28));
        sv.addView(l, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return l;
    }

    private View equalizer(int wDp, int hDp) {
        MusicViews.Equalizer eq = new MusicViews.Equalizer(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(wDp), ui.dp(hDp));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = ui.dp(10);
        eq.setLayoutParams(lp);
        return eq;
    }

    private void keepScreenOn(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---------- Accueil ----------

    private void showHome() {
        screen = SC_HOME;
        keepScreenOn(false);
        game = null;
        LinearLayout l = page();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        // Panneau néon façon icône : accroche + casque/micro + titre
        LinearLayout hero = ui.vbox();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(new MusicViews.Neon(ui.dp(26), ui.dp(2.5f)));
        hero.setPadding(ui.dp(22), ui.dp(26), ui.dp(22), ui.dp(24));
        TextView tag = ui.center("TESTEZ VOS CONNAISSANCES !", 15, Ui.TXT, true);
        tag.setShadowLayer(8, 0, 0, Ui.NEON_BLUE);
        hero.addView(tag);
        android.widget.ImageView art = new android.widget.ImageView(this);
        try (java.io.InputStream in = getAssets().open("hero.webp")) {
            art.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (Exception ignored) { }
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ui.dp(210), ui.dp(210));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = ui.dp(6);
        hero.addView(art, alp);
        TextView title = ui.center("QUIZZ MUSICAL", 34, Ui.TXT, true);
        title.setShadowLayer(16, 0, 0, Ui.NEON_PINK);
        hero.addView(title, ui.full(4));
        View eq = equalizer(200, 28);
        hero.addView(eq);
        ui.add(l, hero, 6);
        ui.add(l, ui.center("Fredonne. Fais deviner. Marque des points.", 16, Ui.MUTED, false), 12);

        TextView play = ui.button("▶  Nouvelle partie", Ui.PINK, Ui.TXT);
        play.setOnClickListener(v -> showSetup());
        ui.add(l, play, 40);

        TextView rules = ui.button("📖  Règles du jeu", Ui.CARD2, Ui.TXT);
        rules.setOnClickListener(v -> showRules());
        ui.add(l, rules, 12);

        int played = prefs.getStringSet("played", new HashSet<String>()).size();
        LinearLayout info = ui.card();
        int ru = Styles.pool(catalog, "ru", 0).size();
        int en = Styles.pool(catalog, "en", 0).size();
        int cl = Styles.pool(catalog, "classique", 0).size();
        int it = Styles.pool(catalog, "inter", 0).size();
        info.addView(ui.text("📀 " + (catalog.size() - ru - en - cl - it) + " chansons du Top 50 et des hit-parades depuis 1960", 14, Ui.MUTED, false));
        info.addView(ui.text("🌍 dont " + Styles.pool(catalog, "es", 0).size() + " en espagnol, " + Styles.pool(catalog, "it", 0).size()
                + " en italien, " + Styles.pool(catalog, "instru", 0).size() + " instrumentaux", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🪆 + module Russie : " + ru + " tubes (titres en cyrillique)", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🇬🇧 + module English : " + en + " tubes mondiaux en anglais", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🇪🇺 + module International : " + it + " tubes connus dans toute l'Europe", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🎻 + module Classique : " + cl + " œuvres célèbres", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🔁 " + played + " déjà jouées (évitées aux prochains tirages)", 14, Ui.MUTED, false), ui.full(6));
        ui.add(l, info, 28);

        if (played > 0) {
            TextView reset = ui.button("Réinitialiser l'historique", Ui.BG, Ui.MUTED);
            reset.setTextSize(14);
            reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Remettre toutes les chansons en jeu ?")
                    .setPositiveButton("Oui", (d, w) -> { prefs.edit().remove("played").apply(); showHome(); })
                    .setNegativeButton("Non", null).show());
            ui.add(l, reset, 8);
        }
    }

    private void showRules() {
        String msg = "• Le chanteur reçoit une chanson et une onomatopée tirées au sort (ex. « NA NA NA »). Il doit faire deviner la chanson en la fredonnant uniquement avec cette onomatopée.\n\n"
                + "• Défis : parfois une contrainte s'ajoute (chuchoter, voix de robot, siffler...).\n\n"
                + "• Trouvé : le chanteur ET celui qui a deviné marquent 1 point. En équipes, l'équipe marque 1 point.\n\n"
                + "• Bonus éclair : chanson trouvée en moins de 5 s = +1 point supplémentaire pour chacun.\n\n"
                + "• Passer : le jeu se met en pause, on peut écouter la chanson passée puis reprendre. Une nouvelle chanson est tirée, mais le chanteur perd du temps (pénalité réglable).\n\n"
                + "• Bip bip pendant les 5 dernières secondes.\n\n"
                + "• Fin de passage : les chansons proposées s'affichent, touchez-en une pour l'écouter sur YouTube Music.";
        new AlertDialog.Builder(this).setTitle("Règles").setMessage(msg).setPositiveButton("Compris", null).show();
    }

    // ---------- Réglages ----------

    interface IntCb { void on(int i); }

    private View stepper(String label, String[] options, int idx, IntCb cb) {
        LinearLayout row = ui.hbox();
        row.setBackground(ui.rounded(Ui.CARD, 14));
        row.setPadding(ui.dp(14), ui.dp(8), ui.dp(8), ui.dp(8));
        TextView lab = ui.text(label, 15, Ui.MUTED, false);
        row.addView(lab, ui.weight(1, 0));
        final int[] cur = {idx};
        TextView minus = ui.button("−", Ui.CARD2, Ui.TXT);
        TextView val = ui.center(options[idx], 16, Ui.TXT, true);
        TextView plus = ui.button("+", Ui.CARD2, Ui.TXT);
        minus.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
        plus.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
        minus.setOnClickListener(v -> { cur[0] = (cur[0] - 1 + options.length) % options.length; val.setText(options[cur[0]]); cb.on(cur[0]); });
        plus.setOnClickListener(v -> { cur[0] = (cur[0] + 1) % options.length; val.setText(options[cur[0]]); cb.on(cur[0]); });
        row.addView(minus);
        row.addView(val, new LinearLayout.LayoutParams(ui.dp(130), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(plus);
        return row;
    }

    private String[] range(int from, int to, String suffix) {
        String[] r = new String[to - from + 1];
        for (int i = from; i <= to; i++) r[i - from] = i + suffix;
        return r;
    }

    private void showSetup() {
        screen = SC_SETUP;
        LinearLayout l = page();
        ui.add(l, ui.text("Réglages de la partie", 26, Ui.TXT, true), 0);

        ui.add(l, stepper("Mode", new String[]{"Individuel", "Équipes"}, modeIdx, i -> { modeIdx = i; showSetup(); }), 18);
        if (modeIdx == 0) {
            ui.add(l, stepper("Joueurs", range(2, 12, ""), playersN - 2, i -> playersN = i + 2), 10);
        } else {
            ui.add(l, stepper("Équipes", range(2, 4, ""), teamsN - 2, i -> teamsN = i + 2), 10);
        }
        String[] secs = new String[SECONDS.length];
        for (int i = 0; i < SECONDS.length; i++) secs[i] = SECONDS[i] + " s";
        ui.add(l, stepper("Temps par joueur", secs, secIdx, i -> secIdx = i), 10);
        ui.add(l, stepper("Nombre de tours", range(1, 10, ""), roundsN - 1, i -> roundsN = i + 1), 10);
        String[] pens = new String[PENALTIES.length];
        for (int i = 0; i < PENALTIES.length; i++) pens[i] = PENALTIES[i] == 0 ? "Aucune" : "-" + PENALTIES[i] + " s";
        ui.add(l, stepper("Pénalité si on passe", pens, penIdx, i -> penIdx = i), 10);
        ui.add(l, stepper("Difficulté", DIFFS, diffIdx, i -> diffIdx = i), 10);
        ui.add(l, stepper("Défis surprises", new String[]{"Oui", "Non"}, challengesOn ? 0 : 1, i -> challengesOn = i == 0), 10);
        ui.add(l, stepper("Bonus éclair (< 5 s)", new String[]{"Oui", "Non"}, bonusOn ? 0 : 1, i -> bonusOn = i == 0), 10);
        ui.add(l, stepper("Écouter après un passe", new String[]{"Oui", "Non"}, listenOnPass ? 0 : 1, i -> listenOnPass = i == 0), 10);

        TextView help = ui.text(modeIdx == 0
                ? "Individuel : le chanteur et celui qui devine marquent chacun 1 point."
                : "Équipes : un membre chante, ses coéquipiers devinent. L'équipe marque 1 point par chanson.", 13, Ui.MUTED, false);
        ui.add(l, help, 14);

        TextView next = ui.button("Suivant  ›", Ui.PINK, Ui.TXT);
        next.setOnClickListener(v -> { saveSettings(); showNames(); });
        ui.add(l, next, 20);
    }

    // ---------- Noms ----------

    private void showNames() {
        screen = SC_NAMES;
        boolean teams = modeIdx == 1;
        int n = teams ? teamsN : playersN;
        String saved = prefs.getString(teams ? "names_teams" : "names_solo", "");
        List<String> prev = new ArrayList<>(Arrays.asList(saved.split("\n")));
        LinearLayout l = page();
        ui.add(l, ui.text(teams ? "Noms des équipes" : "Noms des joueurs", 26, Ui.TXT, true), 0);
        nameFields = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            EditText e = new EditText(this);
            e.setHint(teams ? "Équipe " + (i + 1) : "Joueur " + (i + 1));
            if (i < prev.size() && !prev.get(i).trim().isEmpty()) e.setText(prev.get(i));
            e.setTextColor(Ui.TXT);
            e.setHintTextColor(Ui.MUTED);
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            e.setBackground(ui.rounded(Ui.CARD, 14));
            e.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            ui.add(l, e, 10);
            nameFields.add(e);
        }
        TextView go = ui.button("🎲  Tirage au sort du 1er joueur", Ui.PINK, Ui.TXT);
        go.setOnClickListener(v -> startGame(collectNames()));
        ui.add(l, go, 22);
    }

    private List<String> collectNames() {
        boolean teams = modeIdx == 1;
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nameFields.size(); i++) {
            String s = nameFields.get(i).getText().toString().trim();
            sb.append(s).append("\n");
            if (s.isEmpty()) s = (teams ? "Équipe " : "Joueur ") + (i + 1);
            String base = s;
            int k = 2;
            while (seen.contains(s.toLowerCase())) s = base + " " + k++;
            seen.add(s.toLowerCase());
            names.add(s);
        }
        prefs.edit().putString(teams ? "names_teams" : "names_solo", sb.toString()).apply();
        return names;
    }

    // ---------- Partie ----------

    private GameEngine.Config buildConfig(List<String> names) {
        GameEngine.Config c = new GameEngine.Config();
        c.mode = modeIdx == 1 ? GameEngine.MODE_TEAMS : GameEngine.MODE_SOLO;
        c.seconds = SECONDS[secIdx];
        c.rounds = roundsN;
        c.passPenalty = PENALTIES[penIdx];
        c.difficulty = diffIdx;
        c.challenges = challengesOn;
        c.speedBonus = bonusOn;
        c.frenchShare = prefs.getInt("frShare", 75);
        c.names = names;
        return c;
    }

    private void startGame(List<String> names) {
        game = new GameEngine(buildConfig(names), catalog, loadPlayed(), rnd);
        stoppedEarly = false;
        keepScreenOn(true);
        showDraw();
    }

    private void showDraw() {
        screen = SC_GAME;
        LinearLayout l = page();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        ui.add(l, ui.center("🎲 Tirage au sort", 24, Ui.MUTED, true), 60);
        final TextView name = ui.center("", 40, Ui.GOLD, true);
        ui.add(l, name, 40);
        final TextView sub = ui.center("", 18, Ui.TXT, false);
        ui.add(l, sub, 16);
        final LinearLayout after = ui.vbox();
        ui.add(l, after, 30);

        final List<GameEngine.Player> ps = game.players;
        final int steps = 22 + rnd.nextInt(ps.size());
        final int target = game.firstPlayer;
        final int start = ((target - steps) % ps.size() + ps.size()) % ps.size();
        long delay = 0;
        for (int i = 0; i <= steps; i++) {
            final int idx = (start + i) % ps.size();
            final boolean last = i == steps;
            delay += 50 + (long) (i * i * 0.35);
            h.postDelayed(() -> {
                name.setText(ps.get(idx).name);
                if (last) {
                    name.setTextColor(Ui.PINK);
                    sub.setText("commence la partie !");
                    vibrate(120);
                    StringBuilder order = new StringBuilder("Ordre de passage :\n");
                    for (int k = 0; k < ps.size(); k++) order.append(k + 1).append(". ").append(ps.get((target + k) % ps.size()).name).append("\n");
                    after.addView(ui.center(order.toString().trim(), 15, Ui.MUTED, false));
                    TextView go = ui.button("C'est parti !", Ui.PINK, Ui.TXT);
                    go.setOnClickListener(v -> showHandoff());
                    after.addView(go, ui.full(20));
                } else {
                    sound.click();
                }
            }, delay);
        }
    }

    private void showHandoff() {
        screen = SC_GAME;
        LinearLayout l = page();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        ui.add(l, ui.center("Tour " + game.round + " / " + game.config.rounds, 16, Ui.MUTED, true), 20);
        ui.add(l, ui.center("📱 Passe le téléphone à", 18, Ui.TXT, false), 30);
        ui.add(l, ui.center(game.singer().name, 40, Ui.GOLD, true), 8);
        l.addView(equalizer(160, 30));
        ui.add(l, ui.center(game.isTeams()
                ? "Un membre de l'équipe chante, les autres devinent.\nLes autres équipes ne regardent pas l'écran !"
                : "Ne montre pas l'écran aux autres joueurs !", 14, Ui.MUTED, false), 12);
        TextView ready = ui.button("Je suis prêt  ›", Ui.PINK, Ui.TXT);
        ready.setOnClickListener(v -> showStylePicker());
        ui.add(l, ready, 30);
        ui.add(l, scoreboard(false), 30);
    }

    private View scoreboard(boolean withRank) {
        LinearLayout c = ui.card();
        c.addView(ui.text("Classement", 15, Ui.MUTED, true));
        List<GameEngine.Player> r = game.ranking();
        for (int i = 0; i < r.size(); i++) {
            GameEngine.Player p = r.get(i);
            int rank = game.rankOf(r, i);
            LinearLayout row = ui.hbox();
            String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : rank + ".";
            row.addView(ui.text(medal, 18, Ui.TXT, false), new LinearLayout.LayoutParams(ui.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(ui.text(p.name, 17, Ui.TXT, rank == 1), ui.weight(1, 0));
            row.addView(ui.text(p.score + " pt" + (p.score > 1 ? "s" : ""), 17, rank == 1 ? Ui.GOLD : Ui.TXT, true));
            c.addView(row, ui.full(8));
        }
        return c;
    }

    private void showStylePicker() {
        screen = SC_GAME;
        LinearLayout l = page();
        ui.add(l, ui.text(game.singer().name + ", choisis ta playlist", 22, Ui.TXT, true), 0);
        ui.add(l, ui.text("Le chrono démarre dès que tu choisis.", 14, Ui.MUTED, false), 4);

        // Jauge "part de chansons françaises" (tout en haut)
        LinearLayout gauge = ui.card();
        final TextView gLabel = ui.text("", 16, Ui.TXT, true);
        gauge.addView(gLabel);
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(10);
        sb.setProgress(game.config.frenchShare / 10);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.NEON_PINK));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ORANGE));
        gauge.addView(sb, ui.full(8));
        gauge.addView(ui.text("S'applique à Random, aux styles et aux décennies. Les thèmes sous le trait ne sont pas concernés.", 12, Ui.MUTED, false), ui.full(4));
        gLabel.setText("🇫🇷 Chansons françaises : " + game.config.frenchShare + " %");
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                game.config.frenchShare = p * 10;
                gLabel.setText("🇫🇷 Chansons françaises : " + game.config.frenchShare + " %");
                prefs.edit().putInt("frShare", game.config.frenchShare).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) { }
        });
        ui.add(l, gauge, 14);

        // Tuile vedette pleine largeur, plus grosse : Random
        int[] c0 = Styles.COLORS[0];
        TextView star = ui.gbutton(Styles.EMOJI[0] + "  " + Styles.LABELS[0].toUpperCase(), c0[0], c0[1]);
        star.setTextSize(26);
        star.setPadding(ui.dp(10), ui.dp(30), ui.dp(10), ui.dp(30));
        star.setShadowLayer(12, 0, 0, Ui.NEON_PINK);
        star.setOnClickListener(v -> startTurn(Styles.KEYS[0]));
        ui.add(l, star, 16);

        addTileGrid(l, 1, Styles.FIRST_SPECIAL);

        // Trait de séparation : langues et thèmes spéciaux (hors jauge)
        View line = new View(this);
        line.setBackground(ui.gradient(Ui.NEON_BLUE, Ui.NEON_PINK, 2));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(3));
        llp.topMargin = ui.dp(22);
        l.addView(line, llp);
        ui.add(l, ui.text("🌍 Langues & thèmes spéciaux", 15, Ui.MUTED, true), 10);
        addTileGrid(l, Styles.FIRST_SPECIAL, Styles.KEYS.length);
    }

    private void addTileGrid(LinearLayout l, int from, int to) {
        LinearLayout row = null;
        for (int i = from; i < to; i++) {
            if ((i - from) % 2 == 0) {
                row = ui.hbox();
                ui.add(l, row, 12);
            }
            final String key = Styles.KEYS[i];
            int[] col = Styles.COLORS[i % Styles.COLORS.length];
            TextView b = ui.gbutton(Styles.EMOJI[i] + "  " + Styles.LABELS[i], col[0], col[1]);
            b.setTextSize(16);
            b.setPadding(ui.dp(8), ui.dp(18), ui.dp(8), ui.dp(18));
            b.setOnClickListener(v -> startTurn(key));
            row.addView(b, ui.weight(1, 5));
        }
        if ((to - from) % 2 == 1 && row != null) row.addView(new View(this), ui.weight(1, 5));
    }

    // ---------- Jeu chronométré ----------

    private void startTurn(String style) {
        screen = SC_PLAY;
        currentStyle = style;
        totalMs = game.config.seconds * 1000L;
        remainingMs = totalMs;
        lastBeepSec = -1;
        buildPlayScreen();
        nextSong();
        resumeTimer();
    }

    private void buildPlayScreen() {
        LinearLayout l = page();
        playRoot = l;
        LinearLayout top = ui.hbox();
        TextView who = ui.text("🎤 " + game.singer().name, 18, Ui.TXT, true);
        top.addView(who, ui.weight(1, 0));
        TextView pause = ui.button("⏸", Ui.CARD2, Ui.TXT);
        pause.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
        pause.setOnClickListener(v -> { pauseTimer(); showPauseOverlay(); });
        top.addView(pause);
        ui.add(l, top, 0);

        timerText = ui.center("", 64, Ui.YELLOW, true);
        timerText.setShadowLayer(16, 0, 0, Ui.NEON_BLUE);
        ui.add(l, timerText, 6);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GOLD));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.CARD2));
        l.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(10)));
        l.addView(equalizer(220, 22));

        LinearLayout stats = ui.hbox();
        foundText = ui.text("", 14, Ui.MUTED, false);
        scoreText = ui.text("", 14, Ui.MUTED, false);
        scoreText.setGravity(Gravity.END);
        stats.addView(foundText, ui.weight(1, 0));
        stats.addView(scoreText, ui.weight(1, 0));
        ui.add(l, stats, 8);

        songCard = ui.card();
        songCard.setBackground(new MusicViews.Neon(ui.dp(22), ui.dp(2)));
        songCard.setGravity(Gravity.CENTER_HORIZONTAL);
        songCard.setPadding(ui.dp(22), ui.dp(24), ui.dp(22), ui.dp(24));
        ui.add(l, songCard, 14);

        LinearLayout actions = ui.hbox();
        int pen = game.config.passPenalty;
        TextView pass = ui.button(pen > 0 ? "Passer\n-" + pen + " s" : "Passer", Ui.RED, Ui.TXT);
        TextView ok = ui.button("✓ Trouvé !", Ui.GREEN, Ui.TXT);
        ok.setTextSize(22);
        pass.setOnClickListener(v -> onPass());
        ok.setOnClickListener(v -> onFound());
        actions.addView(pass, ui.weight(1, 5));
        actions.addView(ok, ui.weight(1.6f, 5));
        ui.add(l, actions, 18);
    }

    private void nextSong() {
        GameEngine.Proposal p = game.draw(currentStyle);
        songElapsedMs = 0;
        songCard.removeAllViews();
        songCard.addView(ui.center(Styles.label(currentStyle).toUpperCase() + "  •  " + p.song.year, 13, Ui.MUTED, true));
        String genre = Styles.genres(p.song);
        if (!genre.isEmpty()) songCard.addView(ui.center((p.song.hasGenre('C') ? "🎻 " : "🎵 ") + genre, 14, Ui.CYAN, true), ui.full(4));
        songCard.addView(ui.center("Fais deviner avec", 15, Ui.MUTED, false), ui.full(14));
        TextView ono = ui.center(p.onomato, 38, Ui.YELLOW, true);
        ono.setShadowLayer(14, 0, 0, Ui.ORANGE_DARK);
        songCard.addView(ono, ui.full(2));
        if (p.challenge != null) {
            TextView ch = ui.center("⚡ Défi : " + p.challenge, 15, Ui.TXT, true);
            ch.setBackground(ui.rounded(Ui.PINK, 20));
            ch.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = ui.dp(10);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            songCard.addView(ch, lp);
        }
        View sep = new View(this);
        sep.setBackgroundColor(Ui.CARD2);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1));
        slp.topMargin = ui.dp(18);
        songCard.addView(sep, slp);
        songCard.addView(ui.center(p.song.title, 26, Ui.TXT, true), ui.full(16));
        if (p.song.translation != null) {
            TextView tr = ui.center("🇫🇷 " + p.song.translation, 16, Ui.GOLD, false);
            tr.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC);
            songCard.addView(tr, ui.full(4));
        }
        songCard.addView(ui.center(p.song.artist, 18, Ui.MUTED, false), ui.full(4));
        updateStats();
    }

    private void updateStats() {
        int found = 0;
        for (GameEngine.Proposal p : game.currentTurn) if (p.found) found++;
        foundText.setText("✓ " + found + " trouvée" + (found > 1 ? "s" : ""));
        scoreText.setText("Score : " + game.singer().score);
    }

    private void updateTimerUi() {
        long r = Math.max(0, remainingMs);
        int sec = (int) Math.ceil(r / 1000.0);
        timerText.setText(sec >= 60 ? String.format(java.util.Locale.FRANCE, "%d:%02d", sec / 60, sec % 60) : String.valueOf(sec));
        progress.setProgress((int) (1000 * r / Math.max(1, totalMs)));
        boolean alert = r <= 5000;
        timerText.setTextColor(alert ? Ui.TXT : Ui.YELLOW);
        backdrop.setAlert(alert);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(alert ? Ui.RED : Ui.GOLD));
    }

    private void pauseTimer() {
        running = false;
        h.removeCallbacks(ticker);
    }

    private void resumeTimer() {
        running = true;
        lastTick = SystemClock.elapsedRealtime();
        updateTimerUi();
        h.removeCallbacks(ticker);
        h.post(ticker);
    }

    private void onPass() {
        if (!running) return;
        final GameEngine.Proposal passed = game.current;
        game.pass();
        long pen = game.config.passPenalty * 1000L;
        if (pen > 0) {
            remainingMs -= pen;
            flash("-" + game.config.passPenalty + " s", Ui.RED);
            vibrate(60);
        }
        if (remainingMs <= 0) { timeUp(); return; }
        if (listenOnPass && passed != null) {
            pauseTimer();
            updateTimerUi();
            showPassedOverlay(passed);
            return;
        }
        nextSong();
        updateTimerUi();
    }

    /** Chanson passée : le jeu est en pause, on peut l'écouter puis reprendre. */
    private void showPassedOverlay(GameEngine.Proposal p) {
        LinearLayout box = overlayBox("⏭ Chanson passée");
        box.addView(ui.center("⏸ Jeu en pause", 13, Ui.CYAN, true), ui.full(4));
        box.addView(ui.center(p.song.title, 22, Ui.TXT, true), ui.full(14));
        if (p.song.translation != null) box.addView(ui.center("🇫🇷 " + p.song.translation, 14, Ui.GOLD, false), ui.full(2));
        box.addView(ui.center(p.song.artist + " • " + p.song.year, 15, Ui.MUTED, false), ui.full(4));
        TextView listen = ui.button("🎧 Écouter la chanson", Ui.CARD2, Ui.TXT);
        listen.setOnClickListener(v -> openYouTube(p.song));
        box.addView(listen, ui.full(18));
        TextView resume = ui.button("▶ Reprendre le jeu", Ui.PINK, Ui.TXT);
        resume.setOnClickListener(v -> { closeOverlay(); nextSong(); resumeTimer(); });
        box.addView(resume, ui.full(10));
    }

    private void onFound() {
        if (!running) return;
        final long ms = songElapsedMs;
        if (game.isTeams()) {
            scoreFound(-1, ms);
            return;
        }
        pauseTimer();
        LinearLayout box = overlayBox("Qui a trouvé ?");
        LinearLayout row = null;
        int col = 0;
        for (int i = 0; i < game.players.size(); i++) {
            if (i == game.singerIndex()) continue;
            if (col % 2 == 0) { row = ui.hbox(); box.addView(row, ui.full(10)); }
            col++;
            final int idx = i;
            TextView b = ui.button(game.players.get(i).name, Ui.CARD2, Ui.TXT);
            b.setOnClickListener(v -> { closeOverlay(); scoreFound(idx, ms); resumeTimer(); });
            row.addView(b, ui.weight(1, 4));
        }
        if (col % 2 == 1 && row != null) row.addView(new View(this), ui.weight(1, 4));
        TextView cancel = ui.button("Annuler", Ui.BG, Ui.MUTED);
        cancel.setOnClickListener(v -> { closeOverlay(); resumeTimer(); });
        box.addView(cancel, ui.full(16));
    }

    private void scoreFound(int guesser, long ms) {
        game.found(guesser, ms);
        GameEngine.Proposal last = game.history.get(game.history.size() - 1);
        flash(last.bonus ? "⚡ +2 éclair !" : "+1", Ui.GREEN);
        vibrate(40);
        nextSong();
    }

    private void timeUp() {
        pauseTimer();
        game.abandon(); // chanson en cours non trouvée
        sound.endTurn();
        vibrate(250);
        savePlayed();
        backdrop.setAlert(false);
        showTurnRecap();
    }

    private void beep() {
        sound.tick();
        vibrate(40);
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        if (vib == null || !vib.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else vib.vibrate(ms);
    }

    private void flash(String msg, int color) {
        final TextView t = ui.center(msg, 34, color, true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(t, lp);
        t.setAlpha(1f);
        t.animate().translationY(-ui.dp(80)).alpha(0f).setDuration(900).withEndAction(() -> root.removeView(t)).start();
    }

    // ---------- Overlays ----------

    private LinearLayout overlayBox(String title) {
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0xCC000000);
        dim.setClickable(true);
        LinearLayout box = ui.card();
        box.setBackground(new MusicViews.Neon(ui.dp(22), ui.dp(2)));
        box.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        box.addView(ui.center(title, 22, Ui.TXT, true));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.leftMargin = ui.dp(20);
        lp.rightMargin = ui.dp(20);
        dim.addView(box, lp);
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = dim;
        return box;
    }

    private void closeOverlay() {
        if (overlay != null) root.removeView(overlay);
        overlay = null;
    }

    private void showPauseOverlay() {
        if (overlay != null || screen != SC_PLAY) return;
        LinearLayout box = overlayBox("⏸ Pause");
        box.addView(ui.center("Chrono arrêté", 15, Ui.MUTED, false), ui.full(6));
        TextView resume = ui.button("Reprendre", Ui.PINK, Ui.TXT);
        resume.setOnClickListener(v -> { closeOverlay(); resumeTimer(); });
        box.addView(resume, ui.full(16));
        TextView end = ui.button("🏆 Terminer et voir le classement", Ui.CARD2, Ui.TXT);
        end.setTextSize(16);
        end.setOnClickListener(v -> confirmEndEarly());
        box.addView(end, ui.full(10));
        TextView quit = ui.button("Quitter la partie", Ui.BG, Ui.MUTED);
        quit.setOnClickListener(v -> confirmQuit());
        box.addView(quit, ui.full(10));
    }

    private void confirmQuit() {
        boolean wasRunning = running;
        if (wasRunning) pauseTimer();
        new AlertDialog.Builder(this)
                .setMessage("Quitter la partie en cours ?")
                .setPositiveButton("Quitter", (d, w) -> { savePlayed(); showHome(); })
                .setNegativeButton("Continuer", (d, w) -> { if (wasRunning) resumeTimer(); })
                .setNeutralButton("Classement", (d, w) -> endGameEarly())
                .setOnCancelListener(d -> { if (wasRunning) resumeTimer(); })
                .show();
    }

    private void confirmEndEarly() {
        new AlertDialog.Builder(this)
                .setMessage("Arrêter la partie maintenant et afficher le classement ?")
                .setPositiveButton("Terminer", (d, w) -> endGameEarly())
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Arrêt anticipé : la chanson en cours est abandonnée, scores et trophées conservés. */
    private void endGameEarly() {
        if (game == null) { showHome(); return; }
        pauseTimer();
        game.abandon();
        stoppedEarly = !game.isGameOver();
        closeOverlay();
        showFinal();
    }

    // ---------- Récapitulatifs ----------

    private void addSongRows(LinearLayout parent, List<GameEngine.Proposal> list, boolean showSinger) {
        for (final GameEngine.Proposal p : list) {
            LinearLayout row = ui.hbox();
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF), ui.rounded(Ui.CARD, 14), null));
            row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
            row.setClickable(true);
            row.addView(ui.text(p.found ? "✅" : "⏭", 20, Ui.TXT, false), new LinearLayout.LayoutParams(ui.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout col = ui.vbox();
            col.addView(ui.text(p.song.title, 16, Ui.TXT, true));
            if (p.song.translation != null) col.addView(ui.text("🇫🇷 " + p.song.translation, 13, Ui.GOLD, false));
            StringBuilder sub = new StringBuilder(p.song.artist).append(" • ").append(p.song.year);
            String genre = Styles.genres(p.song);
            if (!genre.isEmpty()) sub.append(" • ").append(genre);
            if (showSinger) sub.append("\n🎤 ").append(game.players.get(p.singer).name);
            if (p.found && p.guesser >= 0) sub.append(showSinger ? " → " : "\nTrouvée par ").append(game.players.get(p.guesser).name);
            if (p.found) sub.append(" (").append(Math.max(1, Math.round(p.ms / 1000.0))).append(" s)");
            if (p.bonus) sub.append(" ⚡");
            col.addView(ui.text(sub.toString(), 13, Ui.MUTED, false));
            row.addView(col, ui.weight(1, 0));
            row.addView(ui.text("▶", 20, Ui.PINK, true));
            row.setOnClickListener(v -> openYouTube(p.song));
            ui.add(parent, row, 8);
        }
    }

    private void openYouTube(Song s) {
        Intent i = new Intent(this, YouTubeActivity.class);
        i.putExtra(YouTubeActivity.EXTRA_QUERY, s.query());
        i.putExtra(YouTubeActivity.EXTRA_TITLE, s.title);
        i.putExtra(YouTubeActivity.EXTRA_ARTIST, s.artist);
        startActivity(i);
    }

    private void showTurnRecap() {
        screen = SC_GAME;
        List<GameEngine.Proposal> turn = new ArrayList<>(game.currentTurn);
        int found = 0;
        for (GameEngine.Proposal p : turn) if (p.found) found++;
        String singer = game.singer().name;
        boolean endOfRound = game.isLastTurnOfRound();
        int round = game.round;

        LinearLayout l = page();
        ui.add(l, ui.center("⏰ Temps écoulé !", 28, Ui.TXT, true), 0);
        ui.add(l, ui.center(singer + " a fait deviner " + found + " chanson" + (found > 1 ? "s" : ""), 17, Ui.GOLD, true), 8);
        ui.add(l, ui.text("Chansons proposées (touchez pour écouter) :", 14, Ui.MUTED, false), 18);
        addSongRows(l, turn, false);
        if (turn.isEmpty()) ui.add(l, ui.text("Aucune chanson.", 14, Ui.MUTED, false), 8);

        TextView next = ui.button(endOfRound ? "Voir le classement du tour  ›" : "Joueur suivant  ›", Ui.PINK, Ui.TXT);
        next.setOnClickListener(v -> {
            boolean more = game.nextTurn();
            if (endOfRound) showRoundEnd(round, more);
            else showHandoff();
        });
        ui.add(l, next, 22);
    }

    private void showRoundEnd(int round, boolean more) {
        screen = SC_GAME;
        if (!more) { showFinal(); return; }
        LinearLayout l = page();
        ui.add(l, ui.center("Fin du tour " + round, 28, Ui.TXT, true), 0);
        ui.add(l, scoreboard(true), 16);
        ui.add(l, ui.text("Chansons du tour :", 14, Ui.MUTED, false), 18);
        addSongRows(l, game.roundProposals(round), true);
        TextView next = ui.button("Tour " + game.round + "  ›", Ui.PINK, Ui.TXT);
        next.setOnClickListener(v -> showHandoff());
        ui.add(l, next, 22);
    }

    private View statsCard(PlayerStats ps) {
        LinearLayout c = ui.card();
        c.addView(ui.text(ps.name, 18, Ui.GOLD, true));
        String avg = ps.avgMs() >= 0 ? Math.round(ps.avgMs() / 1000.0) + " s" : "-";
        String nums = "🎤 " + ps.found + "/" + ps.proposed + " trouvées (" + ps.successPct() + " %)"
                + "   ⏱ " + avg + " / chanson"
                + "\n⏭ " + ps.passes + " passe" + (ps.passes > 1 ? "s" : "")
                + "   ⚡ " + ps.bonus + " éclair" + (ps.bonus > 1 ? "s" : "")
                + (game.isTeams() ? "" : "   🎯 " + ps.guessed + " devinée" + (ps.guessed > 1 ? "s" : ""));
        c.addView(ui.text(nums, 14, Ui.MUTED, false), ui.full(6));
        c.addView(ui.text("💪 Points forts", 15, Ui.GREEN, true), ui.full(10));
        for (String s : ps.strengths) c.addView(ui.text("• " + s, 14, Ui.TXT, false), ui.full(2));
        c.addView(ui.text("🎯 À travailler", 15, Ui.PINK, true), ui.full(10));
        for (String s : ps.weaknesses) c.addView(ui.text("• " + s, 14, Ui.TXT, false), ui.full(2));
        return c;
    }

    private void showFinal() {
        screen = SC_FINAL;
        sound.endGame();
        keepScreenOn(false);
        savePlayed();
        LinearLayout l = page();
        List<GameEngine.Player> r = game.ranking();
        ui.add(l, ui.center("🏆", 64, Ui.TXT, false), 0);
        ui.add(l, ui.center(r.get(0).name, 32, Ui.GOLD, true), 0);
        int winners = 0;
        for (int i = 0; i < r.size(); i++) if (game.rankOf(r, i) == 1) winners++;
        ui.add(l, ui.center(winners > 1 ? "Égalité en tête !" : "remporte la partie !", 17, Ui.TXT, false), 4);
        if (stoppedEarly) ui.add(l, ui.center("Partie arrêtée au tour " + Math.min(game.round, game.config.rounds) + " / " + game.config.rounds, 14, Ui.MUTED, false), 6);
        ui.add(l, scoreboard(true), 20);

        LinearLayout st = ui.card();
        st.addView(ui.text("Les trophées", 15, Ui.MUTED, true));
        GameEngine.Player bestSinger = null, bestGuesser = null, fastest = null, passer = null;
        for (GameEngine.Player p : game.players) {
            if (bestSinger == null || p.sungFound > bestSinger.sungFound) bestSinger = p;
            if (bestGuesser == null || p.guessed > bestGuesser.guessed) bestGuesser = p;
            if (p.bestMs >= 0 && (fastest == null || p.bestMs < fastest.bestMs)) fastest = p;
            if (passer == null || p.passes > passer.passes) passer = p;
        }
        if (bestSinger != null && bestSinger.sungFound > 0)
            st.addView(ui.text("🎤 Meilleur chanteur : " + bestSinger.name + " (" + bestSinger.sungFound + ")", 15, Ui.TXT, false), ui.full(8));
        if (!game.isTeams() && bestGuesser != null && bestGuesser.guessed > 0)
            st.addView(ui.text("🎯 Meilleure oreille : " + bestGuesser.name + " (" + bestGuesser.guessed + ")", 15, Ui.TXT, false), ui.full(8));
        if (fastest != null)
            st.addView(ui.text("⚡ Plus rapide : " + fastest.name + " - « " + fastest.bestSong + " » en " + Math.max(1, Math.round(fastest.bestMs / 1000.0)) + " s", 15, Ui.TXT, false), ui.full(8));
        if (passer != null && passer.passes > 0)
            st.addView(ui.text("🙈 Roi du joker : " + passer.name + " (" + passer.passes + " passes)", 15, Ui.TXT, false), ui.full(8));
        ui.add(l, st, 14);

        ui.add(l, ui.text("Statistiques par joueur", 18, Ui.TXT, true), 22);
        for (PlayerStats ps : PlayerStats.compute(game)) ui.add(l, statsCard(ps), 10);

        LinearLayout btns = ui.hbox();
        TextView again = ui.button("🔁 Revanche", Ui.PINK, Ui.TXT);
        again.setOnClickListener(v -> {
            List<String> names = new ArrayList<>();
            for (GameEngine.Player p : game.players) names.add(p.name);
            startGame(names);
        });
        TextView home = ui.button("Accueil", Ui.CARD2, Ui.TXT);
        home.setOnClickListener(v -> showHome());
        btns.addView(again, ui.weight(1, 5));
        btns.addView(home, ui.weight(1, 5));
        ui.add(l, btns, 18);

        ui.add(l, ui.text("Toutes les chansons de la partie (touchez pour écouter) :", 14, Ui.MUTED, false), 22);
        for (int rr = 1; rr <= game.config.rounds; rr++) {
            List<GameEngine.Proposal> list = game.roundProposals(rr);
            if (list.isEmpty()) continue;
            ui.add(l, ui.text("Tour " + rr, 16, Ui.GOLD, true), 14);
            addSongRows(l, list, true);
        }
    }
}
