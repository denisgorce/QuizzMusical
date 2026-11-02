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
    private static final int[] BLIND_SONGS = {10, 15, 20, 30, 50};
    private static final int[] LISTEN = {10, 15, 20, 30, 45};
    private static final String[] DIFFS = {"Mix", "Incontournables", "Connaisseurs"};

    private static final int SC_HOME = 0, SC_SETUP = 1, SC_NAMES = 2, SC_GAME = 3, SC_PLAY = 4, SC_FINAL = 5, SC_THEME = 6, SC_BLIND = 7;

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
    private boolean challengesOn, bonusOn, listenOnPass, catchUpOn;
    private String themeKey = "random";
    private int gameType, blindIdx = 1, listenIdx = 2;
    private boolean blindMiddle = true;
    private int frShare = 75;
    private List<EditText> nameFields = new ArrayList<>();

    // Partie
    private GameEngine game;
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
    private BlindAudio audio;
    private boolean blindActive;
    private int blindFails;

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
            if (remainingMs <= 0) {
                if (game != null && game.config.blind) blindTimeUp(); else timeUp();
                return;
            }
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
        if (audio != null) audio.pause();
        savePlayed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        sound.shutdown();
        if (audio != null) audio.release();
    }

    @Override
    public void onBackPressed() {
        if (overlay != null && screen == SC_PLAY && running) return;
        switch (screen) {
            case SC_HOME: super.onBackPressed(); break;
            case SC_SETUP: showHome(); break;
            case SC_THEME: showSetup(); break;
            case SC_BLIND: confirmQuit(); break;
            case SC_NAMES: showThemePicker(); break;
            case SC_FINAL: showHome(); break;
            default: confirmQuit(); break;
        }
    }

    // ---------- Données ----------

    private void loadCatalog() {
        List<String> lines = new ArrayList<>();
        for (String f : new String[]{"songs.txt", "songs_monde.txt", "songs_ru.txt", "songs_en.txt", "songs_classique.txt", "songs_inter.txt", "songs_film.txt"}) {
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
        catchUpOn = prefs.getBoolean("catchup", true);
        gameType = prefs.getInt("gameType", 0);
        blindIdx = prefs.getInt("blindSongs", 1);
        listenIdx = prefs.getInt("listen", 2);
        blindMiddle = prefs.getBoolean("blindMiddle", true);
        themeKey = prefs.getString("theme", "random");
        frShare = prefs.getInt("frShare", 75);
    }

    private void saveSettings() {
        prefs.edit().putInt("mode", modeIdx).putInt("players", playersN).putInt("teams", teamsN)
                .putInt("sec", secIdx).putInt("rounds", roundsN).putInt("pen", penIdx).putInt("diff", diffIdx)
                .putBoolean("chal", challengesOn).putBoolean("bonus", bonusOn).putBoolean("listen", listenOnPass).putBoolean("catchup", catchUpOn)
                .putString("theme", themeKey).putInt("frShare", frShare)
                .putInt("gameType", gameType).putInt("blindSongs", blindIdx).putInt("listen", listenIdx)
                .putBoolean("blindMiddle", blindMiddle).apply();
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
        blindActive = false;
        if (audio != null) audio.stop();
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

        TextView play = ui.button("🎤  Fredonne et fais deviner", Ui.PINK, Ui.TXT);
        play.setOnClickListener(v -> { gameType = 0; showSetup(); });
        ui.add(l, play, 40);

        TextView blindBtn = ui.button("🎧  Blind test", Ui.CARD2, Ui.TXT);
        blindBtn.setOnClickListener(v -> { gameType = 1; showSetup(); });
        ui.add(l, blindBtn, 12);

        TextView rules = ui.button("📖  Règles du jeu", Ui.BG, Ui.TXT);
        rules.setOnClickListener(v -> showRules());
        ui.add(l, rules, 12);

        int played = prefs.getStringSet("played", new HashSet<String>()).size();
        LinearLayout info = ui.card();
        int ru = Styles.pool(catalog, "ru", 0).size();
        int en = Styles.pool(catalog, "en", 0).size();
        int cl = Styles.pool(catalog, "classique", 0).size();
        int it = Styles.pool(catalog, "inter", 0).size();
        int fm = Styles.pool(catalog, "film", 0).size();
        info.addView(ui.text("📀 " + (catalog.size() - ru - en - cl - it - fm) + " chansons du Top 50 et des hit-parades depuis 1960", 14, Ui.MUTED, false));
        info.addView(ui.text("🌍 dont " + Styles.pool(catalog, "es", 0).size() + " en espagnol, " + Styles.pool(catalog, "it", 0).size()
                + " en italien, " + Styles.pool(catalog, "instru", 0).size() + " instrumentaux", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🪆 + module Russie : " + ru + " tubes (titres en cyrillique)", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🇬🇧 + module English : " + en + " tubes mondiaux en anglais", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🇪🇺 + module International : " + it + " tubes connus dans toute l'Europe", 14, Ui.MUTED, false), ui.full(6));
        info.addView(ui.text("🎬 + module Musique de film : " + fm + " chansons et thèmes de films", 14, Ui.MUTED, false), ui.full(6));
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
        String msg = "• Le thème est choisi au début de la partie et reste le même jusqu'à la fin.\n\n"
                + "• À chaque passage, le chanteur reçoit une liste de 10 chansons. Pour chacune, une onomatopée tirée au sort (ex. « NA NA NA ») : il doit la faire deviner en fredonnant uniquement avec elle.\n\n"
                + "• Défis : parfois une contrainte s'ajoute (chuchoter, voix de robot, siffler...).\n\n"
                + "• Points : titre trouvé = 1 pt, interprète = 1 pt, les deux = 3 pts. Le chanteur ET celui qui a deviné marquent ces points (en équipes, l'équipe marque).\n\n"
                + "• Liste complète : les 10 chansons trouvées = +10 pts pour le chanteur.\n\n"
                + "• Bonus éclair : trouvé en moins de 5 s = +1 pt supplémentaire pour chacun.\n\n"
                + "• Passer : perte de temps (pénalité réglable). La chanson passée revient en fin de liste s'il reste du temps. Si on l'écoute pendant la pause, elle n'est plus reproposée.\n\n"
                + "• Trouvé : on peut écouter l'original avant de continuer (chrono en pause).\n\n"
                + "• Blind test : l'application joue l'original (titre masqué), tout le monde écoute en même temps. Le premier qui trouve marque 1 (titre), 1 (interprète) ou 3 pts (les deux), +1 en moins de 5 s.\n\n"
                + "• Rattrapage (option) : à la fin du passage, les autres joueurs écoutent l'original des chansons non trouvées et peuvent marquer 1 / 1 / 3 pts. Le chanteur ne joue pas.";
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
        ui.add(l, ui.text(gameType == 1 ? "🎧 Blind test" : "Réglages de la partie", 26, Ui.TXT, true), 0);

        ui.add(l, stepper("Type de jeu", new String[]{"🎤 Fredonne", "🎧 Blind test"}, gameType, i -> { gameType = i; showSetup(); }), 18);
        ui.add(l, stepper("Mode", new String[]{"Individuel", "Équipes"}, modeIdx, i -> { modeIdx = i; showSetup(); }), 10);
        if (modeIdx == 0) {
            ui.add(l, stepper("Joueurs", range(2, 12, ""), playersN - 2, i -> playersN = i + 2), 10);
        } else {
            ui.add(l, stepper("Équipes", range(2, 4, ""), teamsN - 2, i -> teamsN = i + 2), 10);
        }
        TextView help;
        if (gameType == 1) {
            String[] nb = new String[BLIND_SONGS.length];
            for (int i = 0; i < nb.length; i++) nb[i] = BLIND_SONGS[i] + " chansons";
            ui.add(l, stepper("Nombre de chansons", nb, blindIdx, i -> blindIdx = i), 10);
            String[] ls = new String[LISTEN.length];
            for (int i = 0; i < ls.length; i++) ls[i] = LISTEN[i] + " s";
            ui.add(l, stepper("Temps d'écoute", ls, listenIdx, i -> listenIdx = i), 10);
            ui.add(l, stepper("Extrait", new String[]{"Au cœur", "Début"}, blindMiddle ? 0 : 1, i -> blindMiddle = i == 0), 10);
            ui.add(l, stepper("Difficulté", DIFFS, diffIdx, i -> diffIdx = i), 10);
            ui.add(l, stepper("Bonus éclair (< 5 s)", new String[]{"Oui", "Non"}, bonusOn ? 0 : 1, i -> bonusOn = i == 0), 10);
            help = ui.text("Blind test : l'application joue l'original, titre masqué. Tout le monde écoute en même temps ; "
                    + "le premier qui trouve le dit à voix haute, on appuie sur ✋ Trouvé.\n"
                    + "Titre = 1 pt · Interprète = 1 pt · Les deux = 3 pts (+1 si trouvé en moins de 5 s).\n"
                    + "Connexion Internet nécessaire.", 13, Ui.MUTED, false);
        } else {
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
            ui.add(l, stepper("Rattrapage des autres joueurs", new String[]{"Oui", "Non"}, catchUpOn ? 0 : 1, i -> catchUpOn = i == 0), 10);
            help = ui.text((modeIdx == 0
                    ? "Individuel : le chanteur et celui qui devine marquent chacun les points."
                    : "Équipes : un membre chante, ses coéquipiers devinent. L'équipe marque les points.")
                    + "\nTitre = 1 pt · Interprète = 1 pt · Les deux = 3 pts · 10 chansons trouvées = +10 pts."
                    + "\nRattrapage : en fin de passage, les autres écoutent l'original des chansons non trouvées et peuvent marquer.", 13, Ui.MUTED, false);
        }
        ui.add(l, help, 14);

        TextView next = ui.button("Suivant  ›", Ui.PINK, Ui.TXT);
        next.setOnClickListener(v -> { saveSettings(); showThemePicker(); });
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
        TextView go = ui.button(gameType == 1 ? "🎧  Commencer le blind test" : "🎲  Tirage au sort du 1er joueur", Ui.PINK, Ui.TXT);
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
        c.frenchShare = frShare;
        c.theme = themeKey;
        c.catchUp = catchUpOn;
        c.blind = gameType == 1;
        c.blindSongs = BLIND_SONGS[blindIdx];
        c.blindMiddle = blindMiddle;
        if (c.blind) c.seconds = LISTEN[listenIdx];
        c.names = names;
        return c;
    }

    private void startGame(List<String> names) {
        game = new GameEngine(buildConfig(names), catalog, loadPlayed(), rnd);
        stoppedEarly = false;
        keepScreenOn(true);
        if (game.config.blind) showBlindIntro(); else showDraw();
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
        ui.add(l, ui.center(themeTitle(), 15, Ui.CYAN, true), 4);
        ui.add(l, ui.center("📱 Passe le téléphone à", 18, Ui.TXT, false), 30);
        ui.add(l, ui.center(game.singer().name, 40, Ui.GOLD, true), 8);
        l.addView(equalizer(160, 30));
        ui.add(l, ui.center(game.isTeams()
                ? "Un membre de l'équipe chante, les autres devinent.\nLes autres équipes ne regardent pas l'écran !"
                : "Ne montre pas l'écran aux autres joueurs !", 14, Ui.MUTED, false), 12);
        TextView ready = ui.button("Je suis prêt  ›", Ui.PINK, Ui.TXT);
        ready.setOnClickListener(v -> startTurn());
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

    private String themeTitle() {
        String k = game != null ? game.config.theme : themeKey;
        for (int i = 0; i < Styles.KEYS.length; i++) if (Styles.KEYS[i].equals(k)) return Styles.EMOJI[i] + " Thème : " + Styles.LABELS[i];
        return k;
    }

    /** Choix du thème, une seule fois pour toute la partie. */
    private void showThemePicker() {
        screen = SC_THEME;
        LinearLayout l = page();
        ui.add(l, ui.text("Choisis le thème de la partie", 22, Ui.TXT, true), 0);
        ui.add(l, ui.text("Il restera le même jusqu'à la fin de la partie.", 14, Ui.MUTED, false), 4);

        // Jauge "part de chansons françaises" (tout en haut)
        LinearLayout gauge = ui.card();
        final TextView gLabel = ui.text("", 16, Ui.TXT, true);
        gauge.addView(gLabel);
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(10);
        sb.setProgress(frShare / 10);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.NEON_PINK));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ORANGE));
        gauge.addView(sb, ui.full(8));
        gauge.addView(ui.text("S'applique à Random, aux styles et aux décennies. Les thèmes sous le trait ne sont pas concernés.", 12, Ui.MUTED, false), ui.full(4));
        gLabel.setText("🇫🇷 Chansons françaises : " + frShare + " %");
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean fromUser) {
                frShare = p * 10;
                gLabel.setText("🇫🇷 Chansons françaises : " + frShare + " %");
                prefs.edit().putInt("frShare", frShare).apply();
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
        star.setOnClickListener(v -> chooseTheme(Styles.KEYS[0]));
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

    private void chooseTheme(String key) {
        themeKey = key;
        saveSettings();
        showNames();
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
            b.setOnClickListener(v -> chooseTheme(key));
            row.addView(b, ui.weight(1, 5));
        }
        if ((to - from) % 2 == 1 && row != null) row.addView(new View(this), ui.weight(1, 5));
    }

    // ---------- Jeu chronométré ----------

    private void startTurn() {
        screen = SC_PLAY;
        game.startTurn();
        totalMs = game.config.seconds * 1000L;
        remainingMs = totalMs;
        lastBeepSec = -1;
        buildPlayScreen();
        showCurrent();
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

    /** Affiche la chanson en cours de la liste ; fin du passage si la liste est vide. */
    private void showCurrent() {
        GameEngine.Proposal p = game.current;
        if (p == null) { turnOver(); return; }
        p.shown = true;
        songElapsedMs = 0;
        songCard.removeAllViews();
        songCard.addView(ui.center("CHANSON " + game.currentNumber() + " / " + GameEngine.TURN_SONGS + "  •  " + p.song.year, 13, Ui.MUTED, true));
        String genre = Styles.genres(p.song);
        if (!genre.isEmpty()) songCard.addView(ui.center((p.song.hasGenre('M') ? "🎬 " : p.song.hasGenre('C') ? "🎻 " : "🎵 ") + genre, 14, Ui.CYAN, true), ui.full(4));
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
        if (p.song.noteLine() != null) {
            TextView tr = ui.center(p.song.noteLine(), 16, Ui.GOLD, false);
            tr.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC);
            songCard.addView(tr, ui.full(4));
        }
        songCard.addView(ui.center(p.song.artist, 18, Ui.MUTED, false), ui.full(4));
        updateStats();
    }

    private void updateStats() {
        int found = game.foundCount();
        foundText.setText("✓ " + found + " / " + GameEngine.TURN_SONGS + "  ·  " + game.queue.size() + " restante" + (game.queue.size() > 1 ? "s" : ""));
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
        if (blindActive && audio != null) audio.pause();
    }

    private void resumeTimer() {
        if (blindActive && audio != null) audio.resume();
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
        showCurrent();
        updateTimerUi();
    }

    /** Chanson passée : le jeu est en pause, on peut l'écouter puis reprendre. */
    private void showPassedOverlay(GameEngine.Proposal p) {
        LinearLayout box = overlayBox("⏭ Chanson passée");
        box.addView(ui.center("⏸ Jeu en pause", 13, Ui.CYAN, true), ui.full(4));
        box.addView(ui.center(p.song.title, 22, Ui.TXT, true), ui.full(14));
        if (p.song.noteLine() != null) box.addView(ui.center(p.song.noteLine(), 14, Ui.GOLD, false), ui.full(2));
        box.addView(ui.center(p.song.artist + " • " + p.song.year, 15, Ui.MUTED, false), ui.full(4));
        box.addView(ui.center("Elle reviendra en fin de liste s'il reste du temps.\nSi tu l'écoutes, elle ne sera plus reproposée.", 12, Ui.MUTED, false), ui.full(10));
        TextView listen = ui.button("🎧 Écouter la chanson", Ui.CARD2, Ui.TXT);
        listen.setOnClickListener(v -> {
            if (!p.revealed) game.reveal(p);
            openYouTube(p.song, false);
        });
        box.addView(listen, ui.full(14));
        TextView resume = ui.button("▶ Reprendre le jeu", Ui.PINK, Ui.TXT);
        resume.setOnClickListener(v -> {
            closeOverlay();
            if (game.current == null) { turnOver(); return; }
            showCurrent();
            resumeTimer();
        });
        box.addView(resume, ui.full(10));
    }

    /** Trouvé, étape 1 : ce qui a été trouvé (titre / interprète / les deux), écoute de l'original possible. */
    private void onFound() {
        if (!running) return;
        final GameEngine.Proposal p = game.current;
        if (p == null) return;
        pauseTimer();
        showFoundWhat(p, songElapsedMs);
    }

    private void showFoundWhat(final GameEngine.Proposal p, final long ms) {
        LinearLayout box = overlayBox("🎉 Trouvé !");
        box.addView(ui.center(p.song.title + "\n" + p.song.artist, 15, Ui.MUTED, false), ui.full(6));
        box.addView(ui.center("Qu'a-t-on trouvé ?", 16, Ui.TXT, true), ui.full(16));
        addWhatButtons(box, what -> {
            closeOverlay();
            if (game.isTeams()) scoreFound(-1, what, ms);
            else showFoundWho(p, what, ms);
        });
        TextView listen = ui.button("🎧 Écouter l'original", Ui.CARD2, Ui.TXT);
        listen.setTextSize(15);
        listen.setOnClickListener(v -> openYouTube(p.song, false));
        box.addView(listen, ui.full(16));
        TextView cancel = ui.button("Annuler", Ui.BG, Ui.MUTED);
        cancel.setOnClickListener(v -> { closeOverlay(); resumeTimer(); });
        box.addView(cancel, ui.full(10));
    }

    /** Trouvé, étape 2 (mode individuel) : un appui sur le joueur valide et reprend le jeu. */
    private void showFoundWho(final GameEngine.Proposal p, final int what, final long ms) {
        LinearLayout box = overlayBox("Qui a trouvé ?");
        box.addView(ui.center("+" + GameEngine.pointsFor(what) + " pt" + (GameEngine.pointsFor(what) > 1 ? "s" : "")
                + " pour " + game.singer().name + " et pour le joueur choisi", 14, Ui.MUTED, false), ui.full(6));
        addPlayerButtons(box, game.singerIndex(), idx -> {
            closeOverlay();
            scoreFound(idx, what, ms);
        });
        TextView back = ui.button("‹ Retour", Ui.BG, Ui.MUTED);
        back.setOnClickListener(v -> { closeOverlay(); showFoundWhat(p, ms); });
        box.addView(back, ui.full(16));
    }

    /** Un bouton par joueur (chanteur exclu) : un seul appui suffit. */
    private void addPlayerButtons(LinearLayout box, int exclude, IntCb cb) {
        LinearLayout row = null;
        int col = 0;
        for (int i = 0; i < game.players.size(); i++) {
            if (i == exclude) continue;
            if (col % 2 == 0) { row = ui.hbox(); box.addView(row, ui.full(10)); }
            col++;
            final int idx = i;
            TextView b = ui.button(game.players.get(i).name, Ui.CARD2, Ui.TXT);
            b.setTextSize(16);
            b.setOnClickListener(v -> cb.on(idx));
            row.addView(b, ui.weight(1, 4));
        }
        if (col % 2 == 1 && row != null) row.addView(new View(this), ui.weight(1, 4));
    }

    private void addWhatButtons(LinearLayout box, IntCb cb) {
        TextView title = ui.button("🎵 Titre  +1", Ui.CARD2, Ui.TXT);
        TextView artist = ui.button("🎤 Interprète  +1", Ui.CARD2, Ui.TXT);
        TextView both = ui.button("⭐ Les deux  +3", Ui.GREEN, Ui.TXT);
        title.setTextSize(15);
        artist.setTextSize(15);
        title.setOnClickListener(v -> cb.on(GameEngine.FOUND_TITLE));
        artist.setOnClickListener(v -> cb.on(GameEngine.FOUND_ARTIST));
        both.setOnClickListener(v -> cb.on(GameEngine.FOUND_BOTH));
        LinearLayout row = ui.hbox();
        row.addView(title, ui.weight(1, 4));
        row.addView(artist, ui.weight(1, 4));
        box.addView(row, ui.full(8));
        box.addView(both, ui.full(8));
    }

    private void scoreFound(int guesser, int what, long ms) {
        game.found(guesser, what, ms);
        GameEngine.Proposal last = null;
        for (GameEngine.Proposal q : game.currentTurn) if (q.found && q.ms == ms && q.what == what) last = q;
        int pts = GameEngine.pointsFor(what) + (last != null && last.bonus ? 1 : 0);
        flash("+" + pts + (last != null && last.bonus ? " ⚡" : ""), Ui.GREEN);
        vibrate(40);
        if (game.lastTurnComplete) {
            flash("🏆 Liste complète  +" + GameEngine.FULL_LIST_BONUS, Ui.YELLOW);
            sound.endGame();
            h.postDelayed(this::turnOver, 900);
            return;
        }
        showCurrent();
        resumeTimer();
    }

    private void timeUp() {
        pauseTimer();
        game.abandon(); // chanson en cours non trouvée
        sound.endTurn();
        vibrate(250);
        turnOver();
    }

    /** Fin du passage (temps écoulé ou liste terminée) : rattrapage éventuel puis récapitulatif. */
    private void turnOver() {
        pauseTimer();
        savePlayed();
        backdrop.setAlert(false);
        List<GameEngine.Proposal> missed = game.unfound();
        if (game.config.catchUp && !missed.isEmpty() && game.players.size() > 1) showCatchUp(missed, 0, false);
        else showTurnRecap();
    }

    // ---------- Blind test ----------

    private void showBlindIntro() {
        screen = SC_GAME;
        if (audio == null) audio = new BlindAudio(this);
        blindFails = 0;
        LinearLayout l = page();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        MusicViews.Vinyl vinyl = new MusicViews.Vinyl(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ui.dp(150), ui.dp(150));
        vlp.gravity = Gravity.CENTER_HORIZONTAL;
        vlp.topMargin = ui.dp(10);
        l.addView(vinyl, vlp);
        TextView t = ui.center("BLIND TEST", 34, Ui.TXT, true);
        t.setShadowLayer(16, 0, 0, Ui.NEON_PINK);
        ui.add(l, t, 14);
        ui.add(l, ui.center(themeTitle(), 16, Ui.CYAN, true), 6);
        ui.add(l, ui.center(game.config.blindSongs + " chansons · " + game.config.seconds + " s d'écoute", 15, Ui.MUTED, false), 6);
        ui.add(l, ui.center("🔊 Monte le son et pose le téléphone au centre.\nLe premier qui trouve le crie !", 15, Ui.TXT, false), 22);
        TextView go = ui.button("▶  C'est parti !", Ui.PINK, Ui.TXT);
        go.setOnClickListener(v -> blindNextSong());
        ui.add(l, go, 26);
        ui.add(l, scoreboard(false), 22);
        GameEngine.Proposal first = game.blindPeek();
        if (first != null) audio.prefetch(first.song);
    }

    private void blindNextSong() {
        closeOverlay();
        blindActive = false;
        audio.stop();
        if (game.blindOver()) { showBlindFinal(); return; }
        GameEngine.Proposal p = game.blindNext();
        blindPlay(p);
    }

    private void blindPlay(final GameEngine.Proposal p) {
        renderBlind(p, false);
        audio.play(p.song, game.config.blindMiddle, new BlindAudio.Listener() {
            @Override public void onStarted() {
                if (game == null || game.current != p || screen != SC_BLIND) return;
                blindFails = 0;
                totalMs = game.config.seconds * 1000L;
                remainingMs = totalMs;
                songElapsedMs = 0;
                lastBeepSec = -1;
                blindActive = true;
                foundText.setText("♪ Écoutez !");
                resumeTimer();
                GameEngine.Proposal next = game.blindPeek();
                if (next != null) audio.prefetch(next.song);
            }
            @Override public void onFailed() {
                if (game == null || game.current != p) return;
                blindFails++;
                if (blindFails >= 3) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("Impossible de lire les chansons. Vérifie la connexion Internet.")
                            .setPositiveButton("Réessayer", (d, w) -> { blindFails = 0; blindPlay(game.blindReplace()); })
                            .setNegativeButton("Classement", (d, w) -> showBlindFinal())
                            .setCancelable(false)
                            .show();
                    return;
                }
                blindPlay(game.blindReplace());
            }
        });
    }

    /** Écran d'une chanson : recherche (titre masqué) ou réponse révélée. */
    private void renderBlind(final GameEngine.Proposal p, boolean revealed) {
        screen = SC_BLIND;
        p.shown = true;
        LinearLayout l = page();
        LinearLayout top = ui.hbox();
        top.addView(ui.text("🎧 Chanson " + game.blindIndex + " / " + game.config.blindSongs, 18, Ui.TXT, true), ui.weight(1, 0));
        TextView pause = ui.button("⏸", Ui.CARD2, Ui.TXT);
        pause.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
        pause.setOnClickListener(v -> {
            if (running) { pauseTimer(); showPauseOverlay(); }
            else confirmQuit();
        });
        top.addView(pause);
        ui.add(l, top, 0);
        ui.add(l, ui.text(themeTitle(), 13, Ui.CYAN, false), 2);

        timerText = ui.center(String.valueOf(game.config.seconds), 56, Ui.YELLOW, true);
        timerText.setShadowLayer(16, 0, 0, Ui.NEON_BLUE);
        ui.add(l, timerText, 6);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(revealed ? 0 : 1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GOLD));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.CARD2));
        l.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(10)));
        foundText = ui.center(revealed ? "" : "Recherche du titre…", 14, Ui.MUTED, false);
        scoreText = ui.text("", 1, Ui.MUTED, false);
        ui.add(l, foundText, 8);

        LinearLayout card = ui.vbox();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(new MusicViews.Neon(ui.dp(22), ui.dp(2)));
        card.setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(22));
        ui.add(l, card, 12);
        MusicViews.Vinyl vinyl = new MusicViews.Vinyl(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ui.dp(110), ui.dp(110));
        vlp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(vinyl, vlp);
        if (!revealed) {
            card.addView(ui.center("🎵 Chanson mystère", 22, Ui.YELLOW, true), ui.full(14));
            card.addView(equalizer(200, 26));
            LinearLayout actions = ui.hbox();
            TextView reveal = ui.button("👁 Révéler", Ui.BG, Ui.TXT);
            TextView ok = ui.button("✋ Trouvé !", Ui.GREEN, Ui.TXT);
            ok.setTextSize(22);
            reveal.setOnClickListener(v -> blindReveal(p));
            ok.setOnClickListener(v -> onBlindFound(p));
            actions.addView(reveal, ui.weight(1, 5));
            actions.addView(ok, ui.weight(1.6f, 5));
            ui.add(l, actions, 16);
        } else {
            card.addView(ui.center(p.song.title, 24, Ui.TXT, true), ui.full(14));
            if (p.song.noteLine() != null) card.addView(ui.center(p.song.noteLine(), 15, Ui.GOLD, false), ui.full(2));
            card.addView(ui.center(p.song.artist + " • " + p.song.year, 16, Ui.MUTED, false), ui.full(4));
            String genre = Styles.genres(p.song);
            if (!genre.isEmpty()) card.addView(ui.center("🎵 " + genre, 13, Ui.CYAN, false), ui.full(4));
            String res = p.found
                    ? "✅ " + game.players.get(p.guesser).name + " : " + WHAT[p.what] + " +" + p.points + (p.bonus ? " ⚡+1" : "")
                    : "😶 Personne n'a trouvé";
            card.addView(ui.center(res, 17, p.found ? Ui.GREEN : Ui.MUTED, true), ui.full(14));
            TextView next = ui.button(game.blindOver() ? "🏆  Classement final" : "Chanson suivante  ›", Ui.PINK, Ui.TXT);
            next.setOnClickListener(v -> blindNextSong());
            ui.add(l, next, 16);
        }
        ui.add(l, scoreboard(false), 16);
    }

    private void onBlindFound(final GameEngine.Proposal p) {
        if (!blindActive) return;
        final long ms = songElapsedMs;
        pauseTimer();
        LinearLayout box = overlayBox("✋ Trouvé !");
        addSongInfo(box, p);
        box.addView(ui.center("Qu'a-t-on trouvé ?", 16, Ui.TXT, true), ui.full(16));
        addWhatButtons(box, what -> {
            closeOverlay();
            LinearLayout who = overlayBox("Qui a trouvé ?");
            addSongInfo(who, p);
            who.addView(ui.center(WHAT[what] + " : +" + GameEngine.pointsFor(what) + " pt" + (GameEngine.pointsFor(what) > 1 ? "s" : ""), 15, Ui.GREEN, true), ui.full(10));
            addPlayerButtons(who, -1, idx -> {
                closeOverlay();
                game.blindFound(idx, what, ms);
                flash("+" + (GameEngine.pointsFor(what) + (p.bonus ? 1 : 0)), Ui.GREEN);
                blindReveal(p);
            });
            TextView back = ui.button("‹ Retour", Ui.BG, Ui.MUTED);
            back.setOnClickListener(v -> { closeOverlay(); resumeTimer(); });
            who.addView(back, ui.full(16));
        });
        TextView cancel = ui.button("Annuler", Ui.BG, Ui.MUTED);
        cancel.setOnClickListener(v -> { closeOverlay(); resumeTimer(); });
        box.addView(cancel, ui.full(14));
    }

    /** Réponse de la chanson, pour vérifier ce qu'a dit le joueur avant d'attribuer les points. */
    private void addSongInfo(LinearLayout box, GameEngine.Proposal p) {
        LinearLayout c = ui.vbox();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(ui.rounded(0x33FFFFFF, 16));
        c.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        c.addView(ui.center("🎵 Titre", 12, Ui.MUTED, true));
        c.addView(ui.center(p.song.title, 22, Ui.TXT, true), ui.full(2));
        if (p.song.noteLine() != null) c.addView(ui.center(p.song.noteLine(), 14, Ui.GOLD, false), ui.full(2));
        c.addView(ui.center("🎤 Interprète", 12, Ui.MUTED, true), ui.full(10));
        c.addView(ui.center(p.song.artist, 18, Ui.YELLOW, true), ui.full(2));
        c.addView(ui.center(p.song.year + "  •  " + Styles.genres(p.song), 13, Ui.CYAN, false), ui.full(8));
        box.addView(c, ui.full(12));
    }

    /** Réponse : le chrono s'arrête, la musique continue pendant l'affichage du titre. */
    private void blindReveal(GameEngine.Proposal p) {
        pauseTimer();
        blindActive = false;
        backdrop.setAlert(false);
        if (audio != null) audio.resume();
        renderBlind(p, true);
    }

    private void blindTimeUp() {
        sound.endTurn();
        vibrate(150);
        GameEngine.Proposal p = game.current;
        if (p != null) blindReveal(p);
    }

    private void showBlindFinal() {
        blindActive = false;
        pauseTimer();
        if (audio != null) audio.stop();
        screen = SC_FINAL;
        keepScreenOn(false);
        savePlayed();
        sound.endGame();
        LinearLayout l = page();
        List<GameEngine.Player> r = game.ranking();
        ui.add(l, ui.center("🏆", 64, Ui.TXT, false), 0);
        ui.add(l, ui.center(r.get(0).name, 32, Ui.GOLD, true), 0);
        int winners = 0;
        for (int i = 0; i < r.size(); i++) if (game.rankOf(r, i) == 1) winners++;
        ui.add(l, ui.center(winners > 1 ? "Égalité en tête !" : "remporte le blind test !", 17, Ui.TXT, false), 4);
        ui.add(l, scoreboard(true), 20);

        LinearLayout st = ui.card();
        st.addView(ui.text("Les trophées", 15, Ui.MUTED, true));
        GameEngine.Player ear = null, fastest = null;
        for (GameEngine.Player p : game.players) {
            if (p.guessed > 0 && (ear == null || p.guessed > ear.guessed)) ear = p;
            if (p.bestMs >= 0 && (fastest == null || p.bestMs < fastest.bestMs)) fastest = p;
        }
        int found = 0, total = 0;
        for (GameEngine.Proposal p : game.history) if (p.shown) { total++; if (p.found) found++; }
        st.addView(ui.text("🎵 " + found + " / " + total + " chansons trouvées", 15, Ui.TXT, false), ui.full(8));
        if (ear != null) st.addView(ui.text("🎯 Meilleure oreille : " + ear.name + " (" + ear.guessed + ")", 15, Ui.TXT, false), ui.full(8));
        if (fastest != null)
            st.addView(ui.text("⚡ Plus rapide : " + fastest.name + " - « " + fastest.bestSong + " » en "
                    + Math.max(1, Math.round(fastest.bestMs / 1000.0)) + " s", 15, Ui.TXT, false), ui.full(8));
        ui.add(l, st, 14);

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

        ui.add(l, ui.text("Les chansons du blind test (touchez pour réécouter) :", 14, Ui.MUTED, false), 22);
        addSongRows(l, played(game.history), false);
    }

    /** Rattrapage : les autres joueurs écoutent l'original de chaque chanson non trouvée. */
    private void showCatchUp(final List<GameEngine.Proposal> list, final int idx, boolean revealed) {
        screen = SC_GAME;
        if (idx >= list.size()) { showTurnRecap(); return; }
        final GameEngine.Proposal p = list.get(idx);
        LinearLayout l = page();
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        ui.add(l, ui.center("🎧 Rattrapage", 28, Ui.TXT, true), 0);
        ui.add(l, ui.center("Les autres " + (game.isTeams() ? "équipes" : "joueurs") + " écoutent l'original et tentent leur chance.\n"
                + game.singer().name + " ne joue pas.", 14, Ui.MUTED, false), 6);
        LinearLayout card = ui.vbox();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(new MusicViews.Neon(ui.dp(22), ui.dp(2)));
        card.setPadding(ui.dp(22), ui.dp(24), ui.dp(22), ui.dp(24));
        ui.add(l, card, 16);
        card.addView(ui.center("CHANSON " + (idx + 1) + " / " + list.size(), 13, Ui.MUTED, true));
        TextView play = ui.button("▶ Écouter l'original", Ui.PINK, Ui.TXT);
        play.setOnClickListener(v -> openYouTube(p.song, !revealed));
        card.addView(play, ui.full(14));
        if (!revealed) {
            card.addView(ui.center("🎵 Chanson mystère", 20, Ui.YELLOW, true), ui.full(18));
            TextView show = ui.button("👁 Révéler la réponse", Ui.CARD2, Ui.TXT);
            show.setOnClickListener(v -> showCatchUp(list, idx, true));
            card.addView(show, ui.full(14));
        } else {
            card.addView(ui.center(p.song.title, 24, Ui.TXT, true), ui.full(18));
            if (p.song.noteLine() != null) card.addView(ui.center(p.song.noteLine(), 14, Ui.GOLD, false), ui.full(2));
            card.addView(ui.center(p.song.artist + " • " + p.song.year, 16, Ui.MUTED, false), ui.full(4));
            card.addView(ui.center("Qu'a-t-on trouvé ?", 16, Ui.TXT, true), ui.full(16));
            addWhatButtons(card, what -> {
                LinearLayout box = overlayBox("Qui a trouvé ?");
                box.addView(ui.center("+" + GameEngine.pointsFor(what) + " pt" + (GameEngine.pointsFor(what) > 1 ? "s" : ""), 14, Ui.MUTED, false), ui.full(6));
                addPlayerButtons(box, p.singer, who -> {
                    closeOverlay();
                    game.lateFound(p, who, what);
                    flash("+" + GameEngine.pointsFor(what), Ui.GREEN);
                    showCatchUp(list, idx + 1, false);
                });
                TextView back = ui.button("‹ Retour", Ui.BG, Ui.MUTED);
                back.setOnClickListener(v -> closeOverlay());
                box.addView(back, ui.full(16));
            });
            TextView nobody = ui.button("Personne n'a trouvé", Ui.BG, Ui.MUTED);
            nobody.setOnClickListener(v -> showCatchUp(list, idx + 1, false));
            card.addView(nobody, ui.full(12));
        }
        TextView skip = ui.button("Terminer le rattrapage  ›", Ui.BG, Ui.MUTED);
        skip.setTextSize(14);
        skip.setOnClickListener(v -> showTurnRecap());
        ui.add(l, skip, 16);
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
        // Contenu défilant : le panneau n'est jamais coupé, même sur petit écran
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout holder = ui.vbox();
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24));
        holder.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.addView(holder, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = dim;
        return box;
    }

    private void closeOverlay() {
        if (overlay != null) root.removeView(overlay);
        overlay = null;
    }

    private void showPauseOverlay() {
        if (overlay != null || (screen != SC_PLAY && screen != SC_BLIND)) return;
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
        if (game.config.blind) {
            closeOverlay();
            showBlindFinal();
            return;
        }
        game.abandon();
        stoppedEarly = !game.isGameOver();
        closeOverlay();
        showFinal();
    }

    // ---------- Récapitulatifs ----------

    private static final String[] WHAT = {"titre", "interprète", "titre + interprète"};

    private void addSongRows(LinearLayout parent, List<GameEngine.Proposal> list, boolean showSinger) {
        for (final GameEngine.Proposal p : list) {
            LinearLayout row = ui.hbox();
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF), ui.rounded(Ui.CARD, 14), null));
            row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
            row.setClickable(true);
            String icon = p.found ? "✅" : p.lateGuesser >= 0 ? "🔁" : p.revealed ? "🎧" : p.shown ? "⏭" : "⏳";
            row.addView(ui.text(icon, 20, Ui.TXT, false), new LinearLayout.LayoutParams(ui.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout col = ui.vbox();
            col.addView(ui.text(p.song.title, 16, Ui.TXT, true));
            if (p.song.noteLine() != null) col.addView(ui.text(p.song.noteLine(), 13, Ui.GOLD, false));
            StringBuilder sub = new StringBuilder(p.song.artist).append(" • ").append(p.song.year);
            String genre = Styles.genres(p.song);
            if (!genre.isEmpty()) sub.append(" • ").append(genre);
            if (showSinger) sub.append("\n🎤 ").append(game.players.get(p.singer).name);
            if (p.found) {
                if (p.guesser >= 0) sub.append(showSinger ? " → " : "\nTrouvée par ").append(game.players.get(p.guesser).name);
                else if (!showSinger) sub.append("\nTrouvée");
                sub.append(" : ").append(WHAT[p.what]).append(" +").append(p.points);
                sub.append(" (").append(Math.max(1, Math.round(p.ms / 1000.0))).append(" s)");
                if (p.bonus) sub.append(" ⚡");
            } else if (p.lateGuesser >= 0) {
                sub.append("\nRattrapage : ").append(game.players.get(p.lateGuesser).name)
                        .append(" (").append(WHAT[p.lateWhat]).append(" +").append(p.latePoints).append(")");
            } else if (!p.shown) {
                sub.append("\nPas eu le temps");
            }
            col.addView(ui.text(sub.toString(), 13, Ui.MUTED, false));
            row.addView(col, ui.weight(1, 0));
            row.addView(ui.text("▶", 20, Ui.PINK, true));
            row.setOnClickListener(v -> openYouTube(p.song, false));
            ui.add(parent, row, 8);
        }
    }

    private void openYouTube(Song s, boolean hidden) {
        Intent i = new Intent(this, YouTubeActivity.class);
        i.putExtra(YouTubeActivity.EXTRA_QUERY, s.query());
        i.putExtra(YouTubeActivity.EXTRA_TITLE, s.title);
        i.putExtra(YouTubeActivity.EXTRA_ARTIST, s.artist);
        i.putExtra(YouTubeActivity.EXTRA_HIDDEN, hidden);
        startActivity(i);
    }

    /** Chansons effectivement jouées (affichées, rattrapées ou écoutées). */
    private List<GameEngine.Proposal> played(List<GameEngine.Proposal> l) {
        List<GameEngine.Proposal> out = new ArrayList<>();
        for (GameEngine.Proposal p : l) if (p.shown) out.add(p);
        return out;
    }

    private void showTurnRecap() {
        screen = SC_GAME;
        List<GameEngine.Proposal> turn = new ArrayList<>(game.currentTurn);
        int found = game.foundCount();
        boolean full = game.lastTurnComplete;
        String singer = game.singer().name;
        boolean endOfRound = game.isLastTurnOfRound();
        int round = game.round;

        LinearLayout l = page();
        ui.add(l, ui.center(full ? "🏆 Liste complète !" : "⏰ Temps écoulé !", 28, Ui.TXT, true), 0);
        ui.add(l, ui.center(singer + " a fait deviner " + found + " / " + GameEngine.TURN_SONGS + " chanson" + (found > 1 ? "s" : "")
                + (full ? "\n+" + GameEngine.FULL_LIST_BONUS + " pts de bonus" : ""), 17, Ui.GOLD, true), 8);
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
        addSongRows(l, played(game.roundProposals(round)), true);
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
        GameEngine.Player lister = null, catcher = null;
        for (GameEngine.Player p : game.players) {
            if (p.fullLists > 0 && (lister == null || p.fullLists > lister.fullLists)) lister = p;
            if (p.lateGuessed > 0 && (catcher == null || p.lateGuessed > catcher.lateGuessed)) catcher = p;
        }
        if (lister != null)
            st.addView(ui.text("🏆 Liste complète : " + lister.name + " (" + lister.fullLists + " fois)", 15, Ui.TXT, false), ui.full(8));
        if (catcher != null)
            st.addView(ui.text("🔁 Roi du rattrapage : " + catcher.name + " (" + catcher.lateGuessed + ")", 15, Ui.TXT, false), ui.full(8));
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
            List<GameEngine.Proposal> list = played(game.roundProposals(rr));
            if (list.isEmpty()) continue;
            ui.add(l, ui.text("Tour " + rr, 16, Ui.GOLD, true), 14);
            addSongRows(l, list, true);
        }
    }
}
