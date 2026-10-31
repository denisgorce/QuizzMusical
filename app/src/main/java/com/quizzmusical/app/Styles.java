package com.quizzmusical.app;

import java.util.ArrayList;
import java.util.List;

/** Playlists : genre (code) ou décennie. Une chanson peut appartenir à plusieurs playlists. */
public final class Styles {
    public static final String[] KEYS = {"random", "intemporel", "pop", "rock", "disco", "slow", "variete", "60", "70", "80", "90", "2000", "2010", "2020", "es", "it", "instru", "ru", "en", "classique"};
    public static final String[] LABELS = {"Random", "Tubes français intemporels", "Pop", "Rock", "Disco / Dance", "Slow", "Variété", "60's", "70's", "80's", "90's", "2000's", "2010's", "2020's", "Español", "Italiano", "Instrumental", "Russie", "English", "Classique"};
    public static final String[] EMOJI = {"🎲", "⭐", "🎤", "🎸", "🪩", "💞", "🇫🇷", "📻", "🕺", "📼", "💿", "📱", "🎧", "🔥", "🇪🇸", "🇮🇹", "🎹", "🪆", "🇬🇧", "🎻"};
    /** Dégradé de chaque tuile de playlist (même ordre que KEYS). */
    public static final int[][] COLORS = {
            {0xFF46A8F3, 0xFFF683CE}, {0xFFFFC14D, 0xFFE8650A}, {0xFFF7931E, 0xFFE8650A}, {0xFF2F6FD0, 0xFF063B7D},
            {0xFFF683CE, 0xFF9B6BE8}, {0xFFF08AD8, 0xFFA8329A}, {0xFF46A8F3, 0xFFF683CE}, {0xFF8DBDF0, 0xFF2F6FD0},
            {0xFFF7931E, 0xFFF683CE}, {0xFF46A8F3, 0xFF9B6BE8}, {0xFF2F6FD0, 0xFFF683CE}, {0xFF9B6BE8, 0xFF46A8F3},
            {0xFFE8650A, 0xFFA8329A}, {0xFFF683CE, 0xFFF7931E}, {0xFFE8650A, 0xFFD43F3F}, {0xFF2E9E5B, 0xFFD43F3F},
            {0xFF46A8F3, 0xFF2E9E8F}, {0xFFD43F3F, 0xFF2F6FD0}, {0xFF2F6FD0, 0xFFD43F3F}, {0xFF8A94A6, 0xFF4D1B60}
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

    public static boolean matches(Song s, String key) {
        // Modules Russie (U), English (E) et Classique (C) : isolés, jamais tirés dans les autres playlists ni dans Random
        if ("ru".equals(key)) return s.hasGenre('U');
        if ("en".equals(key)) return s.hasGenre('E');
        if ("classique".equals(key)) return s.hasGenre('C');
        if (s.hasGenre('U') || s.hasGenre('E') || s.hasGenre('C')) return false;
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
