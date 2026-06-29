package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KuwoClient {

    private static final String TAG = "lyrics";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final int MIN_SCORE = 58;
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public LyricsResult find(LyricsRequest request) {
        Entry best = best(request, search(request));
        if (best == null) return null;
        String lyrics = lyric(best.id);
        if (!LyricsParser.hasTimedLine(lyrics)) return null;
        return new LyricsResult("Kuwo", best.name, best.artist, best.album, lyrics, best.durationSec * 1000L, true, best.score);
    }

    private List<Entry> search(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String query : keywords(request)) search(entries, seen, request, query);
        return entries;
    }

    private void search(List<Entry> entries, Set<String> seen, LyricsRequest request, String query) {
        HttpUrl url = HttpUrl.parse("https://search.kuwo.cn/r.s").newBuilder()
                .addQueryParameter("all", query)
                .addQueryParameter("ft", "music")
                .addQueryParameter("newsearch", "1")
                .addQueryParameter("itemset", "web_2013")
                .addQueryParameter("client", "kt")
                .addQueryParameter("pn", "0")
                .addQueryParameter("rn", "8")
                .addQueryParameter("rformat", "json")
                .addQueryParameter("encoding", "utf8")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), Map.of("Referer", "https://www.kuwo.cn/")));
            JSONArray array = object.optJSONArray("abslist");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                Entry entry = new Entry();
                entry.id = id(item);
                entry.name = clean(first(item, "SONGNAME", "NAME"));
                entry.artist = clean(first(item, "ARTIST", "AARTIST"));
                entry.album = clean(item.optString("ALBUM"));
                entry.durationSec = parseInt(item.optString("DURATION"));
                if (!TextUtils.isEmpty(entry.id) && !TextUtils.isEmpty(entry.name) && seen.add(entry.id)) entries.add(entry);
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo search failed title=%s error=%s", request.getTitle(), e.getMessage());
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

    private String lyric(String id) {
        String mobi = lyricFromMobi(id);
        if (LyricsParser.hasTimedLine(mobi)) return mobi;
        return lyricFromOpenApi(id);
    }

    private String lyricFromMobi(String id) {
        String query = "type=lyric&req=2&lrcx=1&rid=" + id + "&songname=&artist=&corp=kuwo&fromchannel=bodian";
        HttpUrl url = HttpUrl.parse("http://mlyric.kuwo.cn/mobi.s").newBuilder()
                .addQueryParameter("f", "bodian")
                .addQueryParameter("q", Base64.encodeToString(query.getBytes(), Base64.NO_WRAP))
                .addQueryParameter("uid", "-1")
                .addQueryParameter("token", "")
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), Map.of("Referer", "https://www.kuwo.cn/")));
            JSONObject data = object.optJSONObject("data");
            String content = data == null ? "" : data.optString("content");
            if (TextUtils.isEmpty(content)) return "";
            return new String(Base64.decode(content, Base64.DEFAULT));
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo mobi lyric failed id=%s error=%s", id, e.getMessage());
            return "";
        }
    }

    private String lyricFromOpenApi(String id) {
        HttpUrl url = HttpUrl.parse("https://www.kuwo.cn/openapi/v1/www/lyric/getlyric").newBuilder()
                .addQueryParameter("musicId", id)
                .addQueryParameter("httpsStatus", "1")
                .addQueryParameter("reqId", UUID.randomUUID().toString())
                .build();
        try {
            JSONObject object = new JSONObject(get(url.toString(), Map.of("Referer", "https://www.kuwo.cn/")));
            JSONObject data = object.optJSONObject("data");
            JSONArray array = data == null ? null : data.optJSONArray("lrclist");
            if (array == null || array.length() == 0) return "";
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String text = clean(item.optString("lineLyric"));
                long time = Math.round(parseDouble(item.optString("time")) * 1000);
                if (TextUtils.isEmpty(text)) continue;
                builder.append(formatTime(time)).append(text).append('\n');
            }
            return builder.toString();
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "kuwo lyric failed id=%s error=%s", id, e.getMessage());
            return "";
        }
    }

    private String get(String url, Map<String, String> headers) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", USER_AGENT);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return response.body().string();
        }
    }

    private String id(JSONObject item) {
        String value = first(item, "DC_TARGETID", "MUSICRID", "MP3RID");
        int index = value.lastIndexOf('_');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String clean(String text) {
        return Uri.decode(text == null ? "" : text)
                .replace("\\\\u0026", "&")
                .replace("\\u0026", "&")
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "")
                .trim();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatTime(long timeMs) {
        long minute = timeMs / 60000;
        double second = (timeMs % 60000) / 1000.0;
        return String.format(Locale.US, "[%02d:%05.2f]", minute, second);
    }

    private static class Entry {
        private String id;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;
    }
}
