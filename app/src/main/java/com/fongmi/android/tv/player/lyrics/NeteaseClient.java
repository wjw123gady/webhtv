package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NeteaseClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final int MIN_SCORE = 58;
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)");
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public LyricsResult find(LyricsRequest request) {
        List<LyricsResult> results = findAll(request, 1);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<LyricsResult> findAll(LyricsRequest request, int limit) {
        ArrayList<LyricsResult> results = new ArrayList<>();
        for (Entry entry : ranked(request)) {
            LyricsResult result = toResult(entry);
            if (result == null) continue;
            results.add(result);
            if (results.size() >= Math.max(1, limit)) break;
        }
        return results;
    }

    private LyricsResult toResult(Entry entry) {
        Lyric lyric = lyric(entry.id);
        String text = !TextUtils.isEmpty(lyric.yrc) ? yrcToEnhancedLrc(lyric.yrc) : "";
        if (!LyricsParser.hasTimedLine(text)) text = lyric.lrc;
        if (!LyricsParser.hasTimedLine(text)) return null;
        return new LyricsResult("Netease", entry.name, entry.artist, entry.album, text, entry.durationSec * 1000L, true, entry.score);
    }

    private List<Entry> ranked(LyricsRequest request) {
        ArrayList<Entry> ranked = new ArrayList<>();
        for (Entry entry : search(request)) {
            entry.score = score(request, entry);
            if (entry.score >= MIN_SCORE) ranked.add(entry);
        }
        ranked.sort(Comparator.comparingInt((Entry entry) -> entry.score).reversed());
        return ranked;
    }

    private List<Entry> search(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String keyword : keywords(request)) search(entries, seen, request, keyword);
        return entries;
    }

    private void search(List<Entry> entries, Set<Long> seen, LyricsRequest request, String keyword) {
        HttpUrl url = HttpUrl.parse("https://music.163.com/api/search/get/web").newBuilder()
                .addQueryParameter("s", keyword)
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", "8")
                .addQueryParameter("offset", "0")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject result = object.optJSONObject("result");
            JSONArray array = result == null ? null : result.optJSONArray("songs");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                Entry entry = new Entry();
                entry.id = item.optLong("id");
                entry.name = clean(item.optString("name"));
                entry.artist = artists(item.optJSONArray("artists"));
                JSONObject album = item.optJSONObject("album");
                entry.album = album == null ? "" : clean(album.optString("name"));
                entry.durationSec = Math.round(item.optInt("duration", 0) / 1000f);
                if (entry.id > 0 && !TextUtils.isEmpty(entry.name) && seen.add(entry.id)) entries.add(entry);
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease search failed title=%s error=%s", request.getTitle(), e.getMessage());
        }
    }

    private Entry best(LyricsRequest request, List<Entry> entries) {
        Entry best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Entry entry : entries) {
            int score = score(request, entry);
            if (score <= bestScore) continue;
            entry.score = score;
            best = entry;
            bestScore = score;
        }
        return best != null && bestScore >= MIN_SCORE ? best : null;
    }

    private int score(LyricsRequest request, Entry entry) {
        int score = 0;
        score += textScore(request.getTitle(), entry.name, 58, 32, -50);
        if (!TextUtils.isEmpty(request.getArtist())) score += textScore(request.getArtist(), entry.artist, 26, 14, -8);
        score += durationScore(request.getDurationSec(), entry.durationSec);
        return score;
    }

    private Lyric lyric(long id) {
        Lyric lyric = new Lyric();
        HttpUrl url = HttpUrl.parse("https://music.163.com/api/song/lyric").newBuilder()
                .addQueryParameter("id", String.valueOf(id))
                .addQueryParameter("lv", "1")
                .addQueryParameter("kv", "1")
                .addQueryParameter("tv", "-1")
                .addQueryParameter("yv", "1")
                .addQueryParameter("ytv", "1")
                .addQueryParameter("yrv", "1")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString()));
            JSONObject yrc = object.optJSONObject("yrc");
            JSONObject lrc = object.optJSONObject("lrc");
            lyric.yrc = yrc == null ? "" : yrc.optString("lyric");
            lyric.lrc = lrc == null ? "" : lrc.optString("lyric");
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "netease lyric failed id=%s error=%s", id, e.getMessage());
        }
        return lyric;
    }

    private String yrcToEnhancedLrc(String yrc) {
        StringBuilder builder = new StringBuilder();
        for (String raw : (yrc == null ? "" : yrc).replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher lineMatcher = YRC_LINE.matcher(line);
            if (!lineMatcher.find()) continue;
            long lineStart = parseLong(lineMatcher.group(1));
            long lineDuration = parseLong(lineMatcher.group(2));
            String lineContent = lineMatcher.group(3);
            StringBuilder words = new StringBuilder();
            Matcher wordMatcher = YRC_WORD.matcher(lineContent);
            while (wordMatcher.find()) {
                String word = wordMatcher.group(3);
                if (TextUtils.isEmpty(word)) continue;
                long start = normalizeWordStart(parseLong(wordMatcher.group(1)), lineStart, lineDuration);
                long duration = parseLong(wordMatcher.group(2));
                words.append('<').append(start).append(',').append(Math.max(0, duration)).append('>').append(word);
            }
            if (words.length() > 0) builder.append(formatTime(lineStart)).append(words).append('\n');
        }
        return builder.toString();
    }

    private long normalizeWordStart(long start, long lineStart, long lineDuration) {
        if (lineStart > 2000 && start >= lineStart && (lineDuration <= 0 || start <= lineStart + lineDuration + 500)) return Math.max(0, start - lineStart);
        return Math.max(0, start);
    }

    private List<String> keywords(LyricsRequest request) {
        List<String> keywords = new ArrayList<>();
        String title = request.getTitle();
        String artist = request.getArtist();
        if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(artist)) {
            addKeyword(keywords, title + " - " + artist);
            addKeyword(keywords, title + " " + artist);
        }
        addKeyword(keywords, title);
        return keywords;
    }

    private void addKeyword(List<String> keywords, String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (!TextUtils.isEmpty(value) && !keywords.contains(value)) keywords.add(value);
    }

    private int textScore(String wanted, String actual, int exact, int contains, int mismatch) {
        String a = LyricsMatcher.normalize(wanted);
        String b = LyricsMatcher.normalize(actual);
        if (TextUtils.isEmpty(a)) return 0;
        if (TextUtils.isEmpty(b)) return mismatch / 2;
        if (a.equals(b)) return exact;
        if (a.contains(b) || b.contains(a)) return contains;
        return mismatch;
    }

    private int durationScore(int wantedSec, int actualSec) {
        if (wantedSec <= 0 || actualSec <= 0) return 0;
        int delta = Math.abs(wantedSec - actualSec);
        if (delta <= 2) return 24;
        if (delta <= 5) return 20;
        if (delta <= 10) return 14;
        if (delta <= 20) return 4;
        if (delta <= 40) return -18;
        return -40;
    }

    private String get(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private String artists(JSONArray array) {
        List<String> names = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject artist = array.optJSONObject(i);
                String name = artist == null ? "" : clean(artist.optString("name"));
                if (!TextUtils.isEmpty(name)) names.add(name);
            }
        }
        return TextUtils.join(" / ", names);
    }

    private String clean(String text) {
        return Uri.decode(text == null ? "" : text)
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private String formatTime(long timeMs) {
        long minute = timeMs / 60000;
        long second = timeMs % 60000;
        return String.format(Locale.US, "[%02d:%02d.%03d]", minute, second / 1000, second % 1000);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static class Entry {
        private long id;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;
    }

    private static class Lyric {
        private String yrc;
        private String lrc;
    }
}
