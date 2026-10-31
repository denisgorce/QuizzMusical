package com.quizzmusical.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Logique de jeu, sans dépendance Android (testable en Java pur). */
public class GameEngine {

    public static final int MODE_SOLO = 0;
    public static final int MODE_TEAMS = 1;
    public static final long SPEED_BONUS_MS = 5000;

    public static class Config {
        public int mode = MODE_SOLO;
        public int seconds = 60;
        public int rounds = 3;
        public int passPenalty = 5;
        public int difficulty = 0; // 0 mix, 1 incontournables, 2 connaisseurs
        public boolean challenges = true;
        public boolean speedBonus = true;
        public int frenchShare = 75; // % de chansons françaises (thèmes généraux)
        public List<String> names = new ArrayList<>();
    }

    public static class Player {
        public final String name;
        public int score, sungFound, guessed, passes, bonus;
        public long bestMs = -1;
        public String bestSong;
        Player(String n) { name = n; }
    }

    public static class Proposal {
        public Song song;
        public String style;
        public String onomato;
        public String challenge; // null si aucun défi
        public int singer;
        public int round;
        public boolean found;
        public int guesser = -1;
        public long ms;
        public boolean bonus;
    }

    public static final String[] ONOMATOS = {
            "LA LA LA", "NA NA NA", "TA TA TA", "PAM PAM PAM", "DOU DOU DOU", "TOU DOU DOU", "BA DA BA",
            "PA PA PA", "DI DI DI", "LOU LOU LOU", "TCHI KI TCHA", "BOUM BOUM", "OUH OUH OUH", "YA YA YA",
            "MI MI MI", "DO BI DOU", "BI BOP", "TAGADA", "PO PO PO", "NI NA NI", "DAM DI DAM", "HM HM HM",
            "WO WO WO", "TI TI TI", "BOU BOU BOU", "FA FA FA", "TCHOU TCHOU", "LA LI LOU", "PIOU PIOU",
            "MEUH MEUH", "COIN COIN", "MIAOU MIAOU", "BLA BLA BLA", "SHOUBIDOU", "PIM PAM POUM", "DING DONG"
    };

    public static final String[] CHALLENGES = {
            "En chuchotant", "Avec une voix de robot", "Avec une voix très grave", "Avec une voix très aiguë",
            "En dansant", "Sans montrer tes dents", "Avec l'accent du Sud", "Comme un chanteur d'opéra",
            "Au ralenti", "En accéléré", "En sifflant", "Bouche fermée", "Debout sur une jambe", "En pinçant ton nez"
    };

    public final Config config;
    public final List<Player> players = new ArrayList<>();
    public final List<Proposal> history = new ArrayList<>();
    public final List<Proposal> currentTurn = new ArrayList<>();
    public int firstPlayer;
    public int round = 1;
    public int turnInRound = 0;
    public Proposal current;

    private final List<Song> catalog;
    private final Set<String> played; // historique persistant (anti-répétition)
    private final Set<String> usedThisGame = new HashSet<>();
    private final Random rnd;
    private String lastOnomato = "";

    public GameEngine(Config cfg, List<Song> catalog, Set<String> played, Random rnd) {
        this.config = cfg;
        this.catalog = catalog;
        this.played = played;
        this.rnd = rnd;
        for (String n : cfg.names) players.add(new Player(n));
        firstPlayer = rnd.nextInt(players.size());
    }

    public boolean isTeams() { return config.mode == MODE_TEAMS; }

    public int singerIndex() { return (firstPlayer + turnInRound) % players.size(); }

    public Player singer() { return players.get(singerIndex()); }

    public Set<String> playedSet() { return played; }

    /** Tire une chanson dans la playlist demandée, en évitant les répétitions. */
    public Proposal draw(String styleKey) {
        String style = styleKey;
        List<Song> pool = Styles.pool(catalog, style, config.difficulty);
        if (pool.isEmpty()) pool = Styles.pool(catalog, style, 0);
        if (Styles.usesFrenchShare(style)) {
            // Jauge : chanson française avec la probabilité choisie, sinon étrangère (repli sur l'autre groupe si vide)
            List<Song> fr = new ArrayList<>();
            for (Song s : pool) if (s.hasGenre('F')) fr.add(s);
            List<Song> other = Styles.foreignPool(catalog, style, config.difficulty);
            if (other.isEmpty()) other = Styles.foreignPool(catalog, style, 0);
            boolean wantFr = rnd.nextInt(100) < config.frenchShare;
            List<Song> pick = wantFr ? fr : other;
            if (freshCount(pick) == 0) pick = wantFr ? other : fr;
            if (!pick.isEmpty()) pool = pick;
        }
        if (pool.isEmpty()) pool = Styles.pool(catalog, "random", 0);

        List<Song> fresh = new ArrayList<>();
        for (Song s : pool) if (!usedThisGame.contains(s.id) && !played.contains(s.id)) fresh.add(s);
        if (fresh.isEmpty()) {
            // Playlist épuisée : on oublie l'historique de cette playlist
            for (Song s : pool) played.remove(s.id);
            for (Song s : pool) if (!usedThisGame.contains(s.id)) fresh.add(s);
        }
        if (fresh.isEmpty()) fresh = pool;

        Song song = fresh.get(rnd.nextInt(fresh.size()));
        usedThisGame.add(song.id);
        played.add(song.id);

        Proposal p = new Proposal();
        p.song = song;
        p.style = styleKey;
        p.singer = singerIndex();
        p.round = round;
        p.onomato = nextOnomato();
        if (config.challenges && rnd.nextInt(100) < 30) {
            p.challenge = CHALLENGES[rnd.nextInt(CHALLENGES.length)];
            if ("En sifflant".equals(p.challenge)) p.onomato = "FIUU FIUU";
            else if ("Bouche fermée".equals(p.challenge)) p.onomato = "HMMM HMMM";
        }
        current = p;
        currentTurn.add(p);
        history.add(p);
        return p;
    }

    private int freshCount(List<Song> l) {
        int n = 0;
        for (Song s : l) if (!usedThisGame.contains(s.id)) n++;
        return n;
    }

    private String nextOnomato() {
        String o;
        do { o = ONOMATOS[rnd.nextInt(ONOMATOS.length)]; } while (o.equals(lastOnomato));
        lastOnomato = o;
        return o;
    }

    /** Chanson trouvée. guesser : index du joueur (mode solo) ou -1 (mode équipes). */
    public void found(int guesser, long elapsedMs) {
        if (current == null) return;
        Proposal p = current;
        p.found = true;
        p.guesser = guesser;
        p.ms = elapsedMs;
        Player s = players.get(p.singer);
        s.score += 1;
        s.sungFound += 1;
        boolean bonus = config.speedBonus && elapsedMs < SPEED_BONUS_MS;
        p.bonus = bonus;
        if (bonus) { s.score += 1; s.bonus += 1; }
        if (s.bestMs < 0 || elapsedMs < s.bestMs) { s.bestMs = elapsedMs; s.bestSong = p.song.title; }
        if (!isTeams() && guesser >= 0 && guesser != p.singer) {
            Player g = players.get(guesser);
            g.score += 1;
            g.guessed += 1;
            if (bonus) { g.score += 1; g.bonus += 1; }
        }
        current = null;
    }

    /** Fin du chrono : la chanson en cours reste non trouvée, sans compter de passe. */
    public void abandon() { current = null; }

    public void pass() {
        if (current == null) return;
        players.get(current.singer).passes += 1;
        current = null;
    }

    /** Termine le passage du joueur. Retourne false si la partie est finie. */
    public boolean nextTurn() {
        current = null;
        currentTurn.clear();
        turnInRound++;
        if (turnInRound >= players.size()) {
            turnInRound = 0;
            round++;
        }
        return round <= config.rounds;
    }

    public boolean isLastTurnOfRound() { return turnInRound == players.size() - 1; }

    public boolean isGameOver() { return round > config.rounds; }

    public List<Player> ranking() {
        List<Player> r = new ArrayList<>(players);
        Collections.sort(r, new Comparator<Player>() {
            @Override public int compare(Player a, Player b) {
                if (b.score != a.score) return b.score - a.score;
                return b.sungFound - a.sungFound;
            }
        });
        return r;
    }

    /** Rang (1-based, ex-aequo partagés). */
    public int rankOf(List<Player> ranking, int i) {
        int rank = 1;
        for (int k = 0; k < i; k++) if (ranking.get(k).score > ranking.get(i).score) rank++;
        return rank;
    }

    public List<Proposal> roundProposals(int r) {
        List<Proposal> out = new ArrayList<>();
        for (Proposal p : history) if (p.round == r) out.add(p);
        return out;
    }
}
