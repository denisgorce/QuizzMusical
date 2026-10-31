package com.quizzmusical.app;

import java.util.ArrayList;
import java.util.List;

/** Playlists : genre (code) ou décennie. Une chanson peut appartenir à plusieurs playlists. */
public final class Styles {
    public static final String[] KEYS = {"random", "intemporel", "pop", "rock", "disco", "slow", "variete", "60", "70", "80", "90", "2000", "2010", "2020",
            "inter", "en", "es", "it", "instru", "ru", "classique"};
    public static final String[] LABELS = {"Random", "Tubes français intemporels", "Pop", "Rock", "Disco / Dance", "Slow", "Variété", "60's", "70's", "80's", "90's", "2000's", "2010's", "2020's",
            "International", "English", "Español", "Italiano", "Instrumental", "Russie", "Classique"};
    public static final String[] EMOJI = {"🎲", "⭐", "🎤", "🎸", "🪩", "💞", "🇫🇷", "📻", "🕺", "📼", "💿", "📱", "🎧", "🔥",
            "🇪🇺", "🇬🇧", "🇪🇸", "🇮🇹", "🎹", "🪆", "🎻"};
    /** Premier thème du groupe "langues et thèmes spéciaux" (séparé par un trait, hors jauge française). */
    public static final int FIRST_SPECIAL = 14;
    /** Dégradé de chaque tuile de playlist (même ordre que KEYS). */
    public static final int[][] COLORS = {
            {0xFF46A8F3, 0xFFF683CE}, {0xFFFFC14D, 0xFFE8650A}, {0xFFF7931E, 0xFFE8650A}, {0xFF2F6FD0, 0xFF063B7D},
            {0xFFF683CE, 0xFF9B6BE8}, {0xFFF08AD8, 0xFFA8329A}, {0xFF46A8F3, 0xFFF683CE}, {0xFF8DBDF0, 0xFF2F6FD0},
            {0xFFF7931E, 0xFFF683CE}, {0xFF46A8F3, 0xFF9B6BE8}, {0xFF2F6FD0, 0xFFF683CE}, {0xFF9B6BE8, 0xFF46A8F3},
            {0xFFE8650A, 0xFFA8329A}, {0xFFF683CE, 0xFFF7931E},
            {0xFF1D4ED8, 0xFFFFC14D}, {0xFF2F6FD0, 0xFFD43F3F}, {0xFFE8650A, 0xFFD43F3F}, {0xFF2E9E5B, 0xFFD43F3F},
            {0xFF46A8F3, 0xFF2E9E8F}, {0xFFD43F3F, 0xFF2F6FD0}, {0xFF8A94A6, 0xFF4D1B60}
    };

    private Styles() {}

    public static String label(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return LABELS[i];
        return key;
    }

    /** Style(s) musical(aux) de la chanson, ex. "Rock · Slow". */
    public static String genres(Song s) {
        if (s.hasGenre('C')) return "Classique · " + period(s.year);
        StringBuilder b = new StringBuilder();
        String[][] g = {{"N", "Instrumental"}, {"H", "Espagnol"}, {"I", "Italien"}, {"P", "Pop"}, {"R", "Rock"}, {"D", "Disco / Dance"}, {"S", "Slow"}, {"V", "Variété"}};
        for (String[] x : g) {
            if (s.hasGenre(x[0].charAt(0))) {
                if (b.length() > 0) b.append(" · ");
                b.append(x[1]);
            }
        }
        return b.toString();
    }

    /** Période de la musique classique d'après l'année de composition. */
    public static String period(int y) {
        if (y < 1600) return "Renaissance";
        if (y < 1750) return "Baroque";
        if (y < 1820) return "Classicisme";
        if (y < 1910) return "Romantisme";
        return "XXe siècle";
    }

    /** La jauge "part de chansons françaises" s'applique à ce thème. */
    public static boolean usesFrenchShare(String key) {
        for (int i = 0; i < FIRST_SPECIAL; i++) if (KEYS[i].equals(key)) return !"intemporel".equals(key);
        return false;
    }

    public static boolean matches(Song s, String key) {
        // Modules isolés : Russie (U), English (E), Classique (C), International (X)
        if ("ru".equals(key)) return s.hasGenre('U');
        if ("en".equals(key)) return s.hasGenre('E');
        if ("classique".equals(key)) return s.hasGenre('C');
        if ("inter".equals(key)) return s.hasGenre('X');
        if (s.isModule()) return false;
        return base(s, key);
    }

    /** Critère genre / décennie, sans tenir compte de l'isolement des modules. */
    static boolean base(Song s, String key) {
        switch (key) {
            // Tous les incontournables français, toutes époques confondues
            case "intemporel": return s.hasGenre('F') && s.difficulty == 1;
            case "pop": return s.hasGenre('P');
            case "rock": return s.hasGenre('R');
            case "disco": return s.hasGenre('D');
            case "slow": return s.hasGenre('S');
            case "variete": return s.hasGenre('V');
            case "es": return s.hasGenre('H');
            case "it": return s.hasGenre('I');
            case "instru": return s.hasGenre('N');
            case "60": return s.decade() == 1960;
            case "70": return s.decade() == 1970;
            case "80": return s.decade() == 1980;
            case "90": return s.decade() == 1990;
            case "2000": return s.decade() == 2000;
            case "2010": return s.decade() == 2010;
            case "2020": return s.decade() == 2020;
            default: return true;
        }
    }

    /** Chansons non françaises d'un thème soumis à la jauge : catalogue principal + module English (sans doublon). */
    public static List<Song> foreignPool(List<Song> all, String key, int difficulty) {
        List<Song> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Song s : all) {
            if (s.hasGenre('F') || s.hasGenre('U') || s.hasGenre('C') || s.hasGenre('X')) continue;
            if (difficulty != 0 && s.difficulty != difficulty) continue;
            if (!base(s, key) || !ids.add(s.id)) continue;
            out.add(s);
        }
        return out;
    }

    /** difficulty : 0 mix, 1 incontournables, 2 connaisseurs. */
    public static List<Song> pool(List<Song> all, String key, int difficulty) {
        List<Song> out = new ArrayList<>();
        for (Song s : all) {
            if (!matches(s, key)) continue;
            if (difficulty != 0 && s.difficulty != difficulty) continue;
            out.add(s);
        }
        return out;
    }
}
