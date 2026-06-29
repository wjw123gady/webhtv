package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.player.PlayerManager;
import com.github.catvod.utils.Util;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

public class LyricsRequest {

    private final String key;
    private final String url;
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;

    public LyricsRequest(String key, String url, String title, String artist, String album, long durationMs) {
        this.key = clean(key);
        this.url = clean(url);
        this.title = cleanTitle(title, url);
        this.artist = cleanArtist(artist);
        this.album = clean(album);
        this.durationMs = durationMs > 0 && durationMs != C.TIME_UNSET ? durationMs : 0;
    }

    public static LyricsRequest from(PlayerManager player) {
        MediaMetadata metadata = player.getMetadata();
        String title = metadata == null || metadata.title == null ? "" : metadata.title.toString();
        String artist = metadata == null || metadata.artist == null ? "" : metadata.artist.toString();
        String url = player.getUrl();
        String name = cleanTitle(title, url);
        String singer = cleanArtist(artist);
        String[] split = splitArtistTitle(name);
        if (TextUtils.isEmpty(singer) && split != null) {
            singer = split[0];
            name = split[1];
        }
        singer = normalizeArtistFromEpisode(name, singer);
        return new LyricsRequest(player.getKey(), url, name, singer, "", player.getDuration());
    }

    public String getKey() {
        return key;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getDurationSec() {
        return durationMs <= 0 ? 0 : (int) Math.round(durationMs / 1000.0);
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(title);
    }

    public String signature() {
        return Util.md5(String.join("|", key, url, title, artist, album, String.valueOf(getDurationSec())));
    }

    private static String clean(String text) {
        return Objects.toString(text, "").trim();
    }

    private static String cleanArtist(String text) {
        String value = clean(text);
        if (value.equalsIgnoreCase("unknown artist")) return "";
        if (value.matches(".*第\\s*\\d+\\s*[集期话話].*")) return "";
        return value;
    }

    private static String cleanTitle(String title, String url) {
        String value = clean(title);
        if (TextUtils.isEmpty(value) || value.startsWith("http://") || value.startsWith("https://") || value.startsWith("file://")) value = fileName(url);
        if (TextUtils.isEmpty(value)) value = fileName(title);
        return stripExtension(value);
    }

    private static String fileName(String url) {
        try {
            if (TextUtils.isEmpty(url)) return "";
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            String name = TextUtils.isEmpty(path) ? url : new File(path).getName();
            return Uri.decode(name);
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripExtension(String name) {
        String value = clean(name);
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int dot = value.lastIndexOf('.');
        if (dot > 0 && value.length() - dot <= 6) value = value.substring(0, dot);
        return value.trim();
    }

    private static String[] splitArtistTitle(String title) {
        String value = clean(title);
        String[] separators = new String[]{" - ", " – ", " — ", "_-_"};
        for (String separator : separators) {
            int index = value.indexOf(separator);
            if (index <= 0 || index >= value.length() - separator.length()) continue;
            String artist = value.substring(0, index).trim();
            String name = value.substring(index + separator.length()).trim();
            if (!artist.isEmpty() && !name.isEmpty()) return new String[]{artist, name};
        }
        int hyphen = value.indexOf('-');
        if (hyphen > 0 && hyphen == value.lastIndexOf('-') && hyphen < value.length() - 1) {
            String artist = value.substring(0, hyphen).trim();
            String name = value.substring(hyphen + 1).trim();
            if (artist.length() >= 2 && name.length() >= 2) return new String[]{artist, name};
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" official audio") || lower.endsWith(" official video")) return null;
        return null;
    }

    private static String normalizeArtistFromEpisode(String title, String artist) {
        String name = clean(title);
        String value = cleanArtist(artist);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(value)) return value;
        for (String separator : new String[]{" - ", " – ", " — ", "-"}) {
            if (value.startsWith(name + separator) && value.length() > name.length() + separator.length()) {
                return value.substring(name.length() + separator.length()).trim();
            }
            if (value.endsWith(separator + name) && value.length() > name.length() + separator.length()) {
                return value.substring(0, value.length() - name.length() - separator.length()).trim();
            }
        }
        return value;
    }
}
