package com.chao.peakmusic.local;

import com.chao.peakmusic.model.SongModel;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory index of the permission-scoped MediaStore snapshot. No filesystem traversal. */
public final class LocalLibraryIndex {
    public enum Section { ALL, ARTISTS, ALBUMS, FOLDERS }
    public final List<SongModel> all;
    private final Map<Section, List<Group>> groups = new EnumMap<>(Section.class);

    public LocalLibraryIndex(List<SongModel> songs) {
        Map<String, SongModel> unique = new LinkedHashMap<>();
        for (SongModel song : songs)
            if (song != null && song.getPath() != null && !song.getPath().isEmpty() && !unique.containsKey(song.getPath()))
                unique.put(song.getPath(), song);
        all = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        Collator collator = Collator.getInstance();
        for (Section section : new Section[]{Section.ARTISTS, Section.ALBUMS, Section.FOLDERS}) {
            Map<String, Builder> builders = new LinkedHashMap<>();
            for (SongModel song : all) {
                String title = section == Section.ARTISTS ? text(song.getSinger())
                        : section == Section.ALBUMS ? text(song.getAlbum()) : directory(song);
                String key = section == Section.ALBUMS ? albumKey(song) : title;
                Builder builder = builders.get(key);
                if (builder == null) { builder = new Builder(key, title); builders.put(key, builder); }
                builder.songs.add(song); builder.artists.add(text(song.getSinger()));
            }
            List<Group> result = new ArrayList<>();
            for (Builder builder : builders.values()) result.add(new Group(section, builder));
            Collections.sort(result, (a, b) -> { int name = collator.compare(a.title, b.title); return name == 0 ? a.key.compareTo(b.key) : name; });
            groups.put(section, Collections.unmodifiableList(result));
        }
    }

    public List<Group> groups(Section section) {
        List<Group> result = groups.get(section);
        return result == null ? Collections.emptyList() : result;
    }
    public Group find(Section section, String key) {
        for (Group group : groups(section)) if (group.key.equals(key)) return group;
        return null;
    }

    private static String albumKey(SongModel song) {
        // IDs are scoped to the storage volume; same-titled albums are not one album.
        if (song.getAlbumId() > 0) return text(song.getVolumeName()) + ":id:" + song.getAlbumId();
        String album = text(song.getAlbum()), artist = text(song.getSinger());
        return "name:" + album.length() + ":" + album + ":" + artist;
    }

    public static String directory(SongModel song) {
        String volume = text(song.getVolumeName());
        if (!volume.isEmpty() && song.getRelativePath() != null) {
            String relative = song.getRelativePath().replaceAll("^/+|/+$", "");
            return volume + ":/" + relative;
        }
        String path = song.getFilePath();
        if (path == null || path.isEmpty() || !new File(path).isAbsolute()) return "";
        String parent = new File(path).getParent();
        return parent == null ? "" : parent;
    }

    private static String text(String value) {
        if (value == null || value.trim().equalsIgnoreCase("<unknown>")) return "";
        return value.trim();
    }

    public static final class Group {
        public final Section section;
        public final String key, title;
        /** null means multiple artists, empty means unknown. */
        public final String artist;
        public final List<SongModel> songs;
        private Group(Section section, Builder builder) {
            this.section = section; key = builder.key; title = builder.title;
            artist = builder.artists.size() == 1 ? builder.artists.iterator().next() : null;
            songs = Collections.unmodifiableList(builder.songs);
        }
    }
    private static final class Builder {
        final String key, title;
        final List<SongModel> songs = new ArrayList<>();
        final Set<String> artists = new LinkedHashSet<>();
        Builder(String key, String title) { this.key = key; this.title = title; }
    }
}
