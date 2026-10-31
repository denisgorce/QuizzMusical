package com.quizzmusical.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Statistiques d'un joueur calculées à partir de l'historique de la partie (Java pur). */
public class PlayerStats {
    public final String name;
    public int proposed, found, passes, bonus, guessed, challengeTotal, challengeFound;
    public long totalMs;
    public final List<String> strengths = new ArrayList<>();
    public final List<String> weaknesses = new ArrayList<>();

    private final Map<String, int[]> byStyle = new LinkedHashMap<>();   // [proposées, trouvées]
    private final Map<Integer, Integer> guessedByDecade = new LinkedHashMap<>();

    PlayerStats(String name) { this.name = name; }

    public int successPct() { return proposed == 0 ? 0 : Math.round(100f * found / proposed); }

    public long avgMs() { return found == 0 ? -1 : totalMs / found; }

    public static List<PlayerStats> compute(GameEngine g) {
        List<PlayerStats> out = new ArrayList<>();
        for (GameEngine.Player p : g.players) out.add(new PlayerStats(p.name));
        for (GameEngine.Proposal p : g.history) {
            PlayerStats s = out.get(p.singer);
            s.proposed++;
            int[] st = s.byStyle.get(p.style);
            if (st == null) { st = new int[2]; s.byStyle.put(p.style, st); }
            st[0]++;
            if (p.challenge != null) s.challengeTotal++;
            if (p.found) {
                s.found++;
                s.totalMs += p.ms;
                st[1]++;
                if (p.bonus) s.bonus++;
                if (p.challenge != null) s.challengeFound++;
                if (p.guesser >= 0 && p.guesser != p.singer) {
                    PlayerStats gs = out.get(p.guesser);
                    gs.guessed++;
                    int d = p.song.hasGenre('U') ? -1 : p.song.hasGenre('C') ? -2 : p.song.decade();
                    Integer c = gs.guessedByDecade.get(d);
                    gs.guessedByDecade.put(d, c == null ? 1 : c + 1);
                }
            }
        }
        for (int i = 0; i < out.size(); i++) out.get(i).passes = g.players.get(i).passes;
        double avgGuessed = 0;
        for (PlayerStats s : out) avgGuessed += s.guessed;
        avgGuessed /= Math.max(1, out.size());
        for (PlayerStats s : out) s.analyze(g.isTeams(), avgGuessed);
        return out;
    }

    private void analyze(boolean teams, double avgGuessed) {
        if (proposed == 0) { weaknesses.add("Pas encore chanté : à tester au prochain tour !"); return; }
        int pct = successPct();
        if (proposed >= 3 && pct >= 60) strengths.add("Chanteur efficace : " + pct + " % de chansons trouvées");
        else if (proposed >= 3 && pct < 30) weaknesses.add("Chant peu reconnu : " + pct + " % de chansons trouvées");

        long avg = avgMs();
        if (avg >= 0 && found >= 2 && avg < 10000) strengths.add("Rapide : " + Math.round(avg / 1000.0) + " s en moyenne par chanson");
        else if (avg >= 0 && found >= 2 && avg > 25000) weaknesses.add("Lent à faire deviner : " + Math.round(avg / 1000.0) + " s en moyenne");
        if (bonus >= 2) strengths.add("Roi de l'éclair : " + bonus + " chansons trouvées en moins de 5 s");

        if (passes >= 3 && passes > found) weaknesses.add("Passe souvent : " + passes + " chansons passées");
        else if (proposed >= 4 && passes == 0) strengths.add("Ne lâche rien : aucune chanson passée");

        if (challengeTotal >= 2) {
            int cp = Math.round(100f * challengeFound / challengeTotal);
            if (cp >= 60) strengths.add("À l'aise avec les défis (" + challengeFound + "/" + challengeTotal + ")");
            else if (cp < 30) weaknesses.add("Défis compliqués (" + challengeFound + "/" + challengeTotal + ")");
        }

        String best = null, worst = null;
        double bestR = -1, worstR = 2;
        for (Map.Entry<String, int[]> e : byStyle.entrySet()) {
            int[] v = e.getValue();
            if (v[0] < 2) continue;
            double r = (double) v[1] / v[0];
            if (r > bestR) { bestR = r; best = e.getKey(); }
            if (r < worstR) { worstR = r; worst = e.getKey(); }
        }
        if (best != null && bestR >= 0.5) strengths.add("Playlist forte : " + Styles.label(best) + " (" + Math.round(bestR * 100) + " %)");
        if (worst != null && !worst.equals(best) && worstR < 0.5) weaknesses.add("Playlist difficile : " + Styles.label(worst) + " (" + Math.round(worstR * 100) + " %)");

        if (!teams) {
            if (guessed > 0 && guessed >= avgGuessed * 1.3 && guessed >= 3) strengths.add("Excellente oreille : " + guessed + " chansons devinées");
            else if (avgGuessed >= 2 && guessed <= avgGuessed * 0.5) weaknesses.add("Oreille à affûter : " + guessed + " chanson" + (guessed > 1 ? "s" : "") + " devinée" + (guessed > 1 ? "s" : ""));
            Integer bestDec = null;
            int bestN = 0;
            for (Map.Entry<Integer, Integer> e : guessedByDecade.entrySet()) {
                if (e.getValue() > bestN) { bestN = e.getValue(); bestDec = e.getKey(); }
            }
            if (bestDec != null && bestN >= 2) {
                String lab = bestDec == -1 ? "les tubes russes" : bestDec == -2 ? "la musique classique" : "les années " + (bestDec >= 2000 ? bestDec : bestDec % 100);
                strengths.add("Incollable sur " + lab + " (" + bestN + " devinées)");
            }
        }
        if (strengths.isEmpty()) strengths.add("Régulier, sans point faible marqué");
        if (weaknesses.isEmpty()) weaknesses.add("Rien à signaler, continue comme ça");
    }
}
