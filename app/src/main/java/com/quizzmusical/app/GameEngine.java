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
    /** Nombre de chansons proposées par passage. */
    public static final int TURN_SONGS = 10;
    /** Bonus si les 10 chansons du passage sont toutes trouvées. */
    public static final int FULL_LIST_BONUS = 10;

    /** Ce qui a été trouvé : titre, interprète ou les deux. */
    public static final int FOUND_TITLE = 0, FOUND_ARTIST = 1, FOUND_BOTH = 2;

    public static int pointsFor(int what) { return what == FOUND_BOTH ? 3 : 1; }

    public static class Config {
        public int mode = MODE_SOLO;
        public int seconds = 60;
        public int rounds = 3;
        public int passPenalty = 5;
        public int difficulty = 0; // 0 mix, 1 incontournables, 2 connaisseurs
        public boolean challenges = true;
        public boolean speedBonus = true;
        public boolean catchUp = true; // rattrapage des autres joueurs en fin de passage
        public int frenchShare = 75; // % de chansons françaises (thèmes généraux)
        public String theme = "random"; // thème unique pour toute la partie
        public boolean blind = false;   // mode blind test : l'app joue l'original, tout le monde devine
        public int blindSongs = 15;     // nombre de chansons en blind test
        public boolean blindMiddle = true; // extrait pris au cœur du morceau
        public List<String> names = new ArrayList<>();
    }

    public static class Player {
        public final String name;
        public int score, sungFound, guessed, passes, bonus, lateGuessed, fullLists;
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
        public int what = -1;
        public int points;
        public int guesser = -1;
        public long ms;
        public boolean bonus;
        public boolean shown;         // affichée au moins une fois au chanteur
        public boolean revealed;      // écoutée pendant le passage : retirée de la liste
        public int lateGuesser = -1;  // rattrapage
        public int lateWhat = -1;
        public int latePoints;
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
    /** Les 10 chansons du passage en cours (récapitulatif). */
    public final List<Proposal> currentTurn = new ArrayList<>();
    /** File d'attente : chansons restant à faire deviner (les passées repartent en fin de file). */
    public final List<Proposal> queue = new ArrayList<>();
    public int firstPlayer;
    public int round = 1;
    public int turnInRound = 0;
    public Proposal current;
    public boolean lastTurnComplete;

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

    // ---------- Passage ----------

    /** Prépare les 10 chansons du passage et place la première en cours. */
    public void startTurn() {
        currentTurn.clear();
        queue.clear();
        lastTurnComplete = false;
        for (int i = 0; i < TURN_SONGS; i++) {
            Proposal p = newProposal(config.theme);
            currentTurn.add(p);
            queue.add(p);
            history.add(p);
        }
        current = queue.isEmpty() ? null : queue.get(0);
    }

    public int foundCount() {
        int n = 0;
        for (Proposal p : currentTurn) if (p.found) n++;
        return n;
    }

    /** Position (1-based) de la chanson en cours dans la liste du passage. */
    public int currentNumber() { return current == null ? 0 : currentTurn.indexOf(current) + 1; }

    public boolean turnComplete() { return queue.isEmpty(); }

    private void advance() { current = queue.isEmpty() ? null : queue.get(0); }

    /** Passe : la chanson repart en fin de file (reproposée s'il reste du temps). */
    public void pass() {
        if (current == null) return;
        Proposal p = current;
        players.get(p.singer).passes += 1;
        queue.remove(p);
        queue.add(p);
        advance();
    }

    /** Chanson passée puis écoutée : elle n'est plus reproposée ni jouée en rattrapage. */
    public void reveal(Proposal p) {
        p.revealed = true;
        queue.remove(p);
        advance();
    }

    /**
     * Chanson trouvée. guesser : index du joueur (mode individuel) ou -1 (mode équipes).
     * Le chanteur et celui qui devine marquent chacun 1 (titre), 1 (interprète) ou 3 (les deux).
     */
    public void found(int guesser, int what, long elapsedMs) {
        if (current == null) return;
        Proposal p = current;
        int pts = pointsFor(what);
        p.found = true;
        p.what = what;
        p.points = pts;
        p.guesser = guesser;
        p.ms = elapsedMs;
        Player s = players.get(p.singer);
        s.score += pts;
        s.sungFound += 1;
        boolean bonus = config.speedBonus && elapsedMs < SPEED_BONUS_MS;
        p.bonus = bonus;
        if (bonus) { s.score += 1; s.bonus += 1; }
        if (s.bestMs < 0 || elapsedMs < s.bestMs) { s.bestMs = elapsedMs; s.bestSong = p.song.title; }
        if (!isTeams() && guesser >= 0 && guesser != p.singer) {
            Player g = players.get(guesser);
            g.score += pts;
            g.guessed += 1;
            if (bonus) { g.score += 1; g.bonus += 1; }
        }
        queue.remove(p);
        if (queue.isEmpty() && foundCount() == currentTurn.size()) {
            s.score += FULL_LIST_BONUS;
            s.fullLists += 1;
            lastTurnComplete = true;
        }
        advance();
    }

    /** Fin du chrono : la chanson en cours reste non trouvée. */
    public void abandon() { current = null; }

    /** Chansons non trouvées et non écoutées pendant le passage (rattrapage). */
    public List<Proposal> unfound() {
        List<Proposal> out = new ArrayList<>();
        for (Proposal p : currentTurn) if (!p.found && !p.revealed) out.add(p);
        return out;
    }

    /** Rattrapage : un autre joueur trouve en écoutant l'original. Le chanteur ne marque rien. */
    public void lateFound(Proposal p, int guesser, int what) {
        if (guesser < 0 || guesser == p.singer || p.lateGuesser >= 0) return;
        int pts = pointsFor(what);
        p.lateGuesser = guesser;
        p.lateWhat = what;
        p.latePoints = pts;
        Player g = players.get(guesser);
        g.score += pts;
        g.lateGuessed += 1;
    }

    // ---------- Blind test ----------

    public int blindIndex; // nombre de chansons déjà jouées

    private Proposal peeked;

    /** Chanson suivante déjà tirée (pour précharger son flux audio), sans la compter. */
    public Proposal blindPeek() {
        if (blindIndex >= config.blindSongs) return null;
        if (peeked == null) peeked = newProposal(config.theme);
        return peeked;
    }

    /** Tire la chanson suivante du blind test. */
    public Proposal blindNext() {
        Proposal p = peeked != null ? peeked : newProposal(config.theme);
        peeked = null;
        p.onomato = null;
        p.challenge = null;
        p.singer = -1;
        p.round = 1;
        blindIndex++;
        history.add(p);
        current = p;
        return p;
    }

    /** Chanson introuvable en écoute : remplacée par une autre, sans compter. */
    public Proposal blindReplace() {
        if (current != null) history.remove(current);
        blindIndex--;
        return blindNext();
    }

    public boolean blindOver() { return blindIndex >= config.blindSongs; }

    /** Blind test : seul celui qui trouve marque (1 / 1 / 3, +1 si éclair). */
    public void blindFound(int guesser, int what, long elapsedMs) {
        if (current == null || guesser < 0) return;
        Proposal p = current;
        int pts = pointsFor(what);
        p.found = true;
        p.what = what;
        p.points = pts;
        p.guesser = guesser;
        p.ms = elapsedMs;
        Player g = players.get(guesser);
        g.score += pts;
        g.guessed += 1;
        boolean bonus = config.speedBonus && elapsedMs < SPEED_BONUS_MS;
        p.bonus = bonus;
        if (bonus) { g.score += 1; g.bonus += 1; }
        if (g.bestMs < 0 || elapsedMs < g.bestMs) { g.bestMs = elapsedMs; g.bestSong = p.song.title; }
    }

    // ---------- Tirage ----------

    private Proposal newProposal(String styleKey) {
        Song song = pickSong(styleKey);
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
        return p;
    }

    /** Tire une chanson dans le thème, en évitant les répétitions. */
    private Song pickSong(String style) {
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
            // Thème épuisé : on oublie l'historique de ce thème
            for (Song s : pool) played.remove(s.id);
            for (Song s : pool) if (!usedThisGame.contains(s.id)) fresh.add(s);
        }
        if (fresh.isEmpty()) fresh = pool;

        Song song = fresh.get(rnd.nextInt(fresh.size()));
        usedThisGame.add(song.id);
        played.add(song.id);
        return song;
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

    // ---------- Enchaînement ----------

    /** Termine le passage du joueur. Retourne false si la partie est finie. */
    public boolean nextTurn() {
        current = null;
        currentTurn.clear();
        queue.clear();
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
