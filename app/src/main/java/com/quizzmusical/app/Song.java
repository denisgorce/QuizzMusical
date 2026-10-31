package com.quizzmusical.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Chanson du catalogue (format ligne : annee|artiste|titre|genres|difficulte[|traduction]). */
public class Song {
    public final int year;
    public final String artist;
    public final String title;
    public final String genres; // P,R,D,S,V
    public final int difficulty; // 1 incontournable, 2 connaisseur
    public final String translation; // traduction française (module Russie), sinon null
    public final String id;

    public Song(int year, String artist, String title, String genres, int difficulty) {
        this(year, artist, title, genres, difficulty, null);
    }

    public Song(int year, String artist, String title, String genres, int difficulty, String translation) {
        this.year = year;
        this.artist = artist;
        this.title = title;
        this.genres = genres;
        this.difficulty = difficulty;
        this.translation = translation == null || translation.trim().isEmpty() ? null : translation.trim();
        this.id = key(artist + "|" + title);
    }

    public int decade() { return (year / 10) * 10; }

    public boolean hasGenre(char g) { return genres.indexOf(g) >= 0; }

    public String query() { return artist + " " + title; }

    static String key(String s) {
        String n = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\p{N}|]", "");
    }

    public static Song parse(String line) {
        String[] p = line.trim().split("\\|");
        if (p.length != 5 && p.length != 6) return null;
        try {
            return new Song(Integer.parseInt(p[0].trim()), p[1].trim(), p[2].trim(), p[3].trim(), Integer.parseInt(p[4].trim()), p.length == 6 ? p[5] : null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Module isolé (Russie, English, Classique, International) : jamais fusionné avec le catalogue principal. */
    public boolean isModule() { return hasGenre('U') || hasGenre('E') || hasGenre('C') || hasGenre('X'); }

    /** Fusionne les doublons hors modules (même artiste + titre) : genres cumulés, difficulté la plus facile. */
    public static List<Song> merge(List<Song> all) {
        java.util.LinkedHashMap<String, Song> map = new java.util.LinkedHashMap<>();
        List<Song> out = new ArrayList<>();
        for (Song s : all) {
            if (s.isModule()) { out.add(s); continue; }
            Song prev = map.get(s.id);
            if (prev == null) { map.put(s.id, s); continue; }
            StringBuilder g = new StringBuilder(prev.genres);
            for (String x : s.genres.split(",")) if (!x.isEmpty() && !prev.hasGenre(x.charAt(0))) g.append(',').append(x);
            map.put(s.id, new Song(prev.year, prev.artist, prev.title, g.toString(), Math.min(prev.difficulty, s.difficulty), prev.translation));
        }
        List<Song> res = new ArrayList<>(map.values());
        res.addAll(out);
        return res;
    }

    public static List<Song> parseAll(List<String> lines) {
        List<Song> out = new ArrayList<>();
        for (String l : lines) {
            if (l.trim().isEmpty() || l.startsWith("#")) continue;
            Song s = parse(l);
            if (s != null) out.add(s);
        }
        return out;
    }
}
