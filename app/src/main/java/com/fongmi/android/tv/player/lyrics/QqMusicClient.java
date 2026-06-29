package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QqMusicClient {

    private static final String TAG = "lyrics";
    private static final String API = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String USER_AGENT = "okhttp/3.14.9";
    private static final String QRC_KEY = "!@#)(*$%123ZXC!@!@#)(NHL";
    private static final int MIN_SCORE = 62;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Pattern QRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern QRC_WORD = Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
    private static final OkHttpClient CLIENT = OkHttp.client()
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private String sessionUid;
    private String sessionSid;
    private String sessionUserIp;
    private long sessionExpireTime;

    public LyricsResult find(LyricsRequest request) {
        Entry best = best(request, search(request));
        if (best == null) return null;
        Lyric lyric = lyric(best);
        String text = !TextUtils.isEmpty(lyric.qrc) ? qrcToEnhancedLrc(lyric.qrc) : "";
        if (!LyricsParser.hasTimedLine(text)) text = lyric.lrc;
        if (!LyricsParser.hasTimedLine(text)) return null;
        return new LyricsResult("QQMusic", best.name, best.artist, best.album, text, best.durationSec * 1000L, true, best.score);
    }

    private List<Entry> search(LyricsRequest request) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String keyword : keywords(request)) search(entries, seen, request, keyword);
        return entries;
    }

    private void search(List<Entry> entries, Set<String> seen, LyricsRequest request, String keyword) {
        try {
            JSONObject data = qqRequest(
                    "DoSearchForQQMusicLite",
                    "music.search.SearchCgiService",
                    new JSONObject()
                            .put("search_id", buildSearchId())
                            .put("remoteplace", "search.android.keyboard")
                            .put("query", keyword)
                            .put("search_type", 0)
                            .put("num_per_page", 8)
                            .put("page_num", 1)
                            .put("highlight", 0)
                            .put("nqc_flag", 0)
                            .put("page_id", 1)
                            .put("grp", 1));
            JSONArray array = data.optJSONObject("body") == null ? null : data.optJSONObject("body").optJSONArray("item_song");
            if (array == null) return;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                Entry entry = new Entry();
                entry.id = parseInt(item.optString("id"));
                entry.mid = item.optString("mid");
                entry.name = clean(item.optString("title"));
                entry.artist = artists(item.optJSONArray("singer"));
                entry.album = item.optJSONObject("album") == null ? "" : clean(item.optJSONObject("album").optString("name"));
                entry.durationSec = item.optInt("interval", 0);
                String key = entry.id > 0 ? String.valueOf(entry.id) : entry.mid;
                if (entry.id > 0 && !TextUtils.isEmpty(entry.name) && seen.add(key)) entries.add(entry);
            }
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "qqmusic search failed title=%s error=%s", request.getTitle(), e.getMessage());
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

    private Lyric lyric(Entry entry) {
        Lyric lyric = new Lyric();
        try {
            JSONObject param = new JSONObject()
                    .put("albumName", encodeBase64(entry.album))
                    .put("crypt", 1)
                    .put("ct", 19)
                    .put("cv", 2111)
                    .put("interval", entry.durationSec)
                    .put("lrc_t", 0)
                    .put("qrc", 1)
                    .put("qrc_t", 0)
                    .put("roma", 1)
                    .put("roma_t", 0)
                    .put("singerName", encodeBase64(entry.artist))
                    .put("songID", entry.id)
                    .put("songName", encodeBase64(entry.name))
                    .put("trans", 1)
                    .put("trans_t", 0)
                    .put("type", 0);
            JSONObject data = qqRequest("GetPlayLyricInfo", "music.musichallSong.PlayLyricInfo", param);
            lyric.qrc = decrypt(data.optString("lyric"));
            lyric.trans = decrypt(data.optString("trans"));
            lyric.roma = decrypt(data.optString("roma"));
            if (data.optInt("qrc_t", 1) == 0) lyric.lrc = lyric.qrc;
            if (TextUtils.isEmpty(lyric.lrc)) lyric.lrc = fallbackLrc(param);
        } catch (Exception e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "qqmusic lyric failed id=%s error=%s", entry.id, e.getMessage());
        }
        return lyric;
    }

    private String fallbackLrc(JSONObject param) {
        try {
            JSONObject data = qqRequest("GetPlayLyricInfo", "music.musichallSong.PlayLyricInfo", new JSONObject(param.toString()).put("qrc", 0).put("qrc_t", 0));
            return decrypt(data.optString("lyric"));
        } catch (Exception e) {
            return "";
        }
    }

    private JSONObject qqRequest(String method, String module, JSONObject param) throws Exception {
        ensureSession();
        JSONObject comm = new JSONObject()
                .put("ct", 11)
                .put("cv", "1003006")
                .put("v", "1003006")
                .put("os_ver", "15")
                .put("phonetype", "24122RKC7C")
                .put("tmeAppID", "qqmusiclight")
                .put("nettype", "NETWORK_WIFI")
                .put("udid", "0");
        if (!TextUtils.isEmpty(sessionUid)) comm.put("uid", sessionUid);
        if (!TextUtils.isEmpty(sessionSid)) comm.put("sid", sessionSid);
        if (!TextUtils.isEmpty(sessionUserIp)) comm.put("userip", sessionUserIp);

        JSONObject response = post(new JSONObject()
                .put("comm", comm)
                .put("request", new JSONObject().put("method", method).put("module", module).put("param", param)));
        if (response.optInt("code", -1) != 0 || response.optJSONObject("request") == null || response.optJSONObject("request").optInt("code", -1) != 0) {
            throw new IllegalStateException("QQMusic API error");
        }
        JSONObject data = response.optJSONObject("request").optJSONObject("data");
        return data == null ? new JSONObject() : data;
    }

    private synchronized void ensureSession() {
        if (!TextUtils.isEmpty(sessionUid) && System.currentTimeMillis() < sessionExpireTime) return;
        try {
            JSONObject response = post(new JSONObject()
                    .put("comm", new JSONObject()
                            .put("ct", 11)
                            .put("cv", "1003006")
                            .put("v", "1003006")
                            .put("os_ver", "15")
                            .put("phonetype", "24122RKC7C")
                            .put("tmeAppID", "qqmusiclight")
                            .put("nettype", "NETWORK_WIFI")
                            .put("udid", "0"))
                    .put("request", new JSONObject()
                            .put("method", "GetSession")
                            .put("module", "music.getSession.session")
                            .put("param", new JSONObject().put("caller", 0).put("uid", "0").put("vkey", 0))));
            if (response.optInt("code", -1) != 0 || response.optJSONObject("request") == null || response.optJSONObject("request").optInt("code", -1) != 0) return;
            JSONObject data = response.optJSONObject("request").optJSONObject("data");
            JSONObject session = data == null ? null : data.optJSONObject("session");
            if (session == null) return;
            sessionUid = session.optString("uid");
            sessionSid = session.optString("sid");
            sessionUserIp = session.optString("userip");
            sessionExpireTime = System.currentTimeMillis() + 3600000L;
        } catch (Exception ignored) {
        }
    }

    private JSONObject post(JSONObject body) throws Exception {
        Request request = new Request.Builder()
                .url(API)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Cookie", "tmeLoginType=-1;")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("HTTP " + response.code());
            return new JSONObject(response.body().string());
        }
    }

    private String decrypt(String encrypted) {
        if (TextUtils.isEmpty(encrypted)) return "";
        try {
            byte[] encryptedBytes = hexToBytes(encrypted);
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(QRC_KEY.getBytes(StandardCharsets.UTF_8), "DESede"));
            return decode(cipher.doFinal(encryptedBytes));
        } catch (Exception e) {
            return "";
        }
    }

    private String decode(byte[] decrypted) {
        String text = inflate(decrypted, false);
        if (!TextUtils.isEmpty(text)) return text;
        text = inflate(decrypted, true);
        if (!TextUtils.isEmpty(text)) return text;
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(decrypted))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            text = output.toString(StandardCharsets.UTF_8.name());
            if (!TextUtils.isEmpty(text)) return text;
        } catch (Exception ignored) {
        }
        text = new String(decrypted, StandardCharsets.UTF_8);
        return text.contains("[") || text.contains("<") ? text : "";
    }

    private String inflate(byte[] data, boolean raw) {
        Inflater inflater = new Inflater(raw);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            inflater.setInput(data);
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count > 0) {
                    output.write(buffer, 0, count);
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break;
                }
            }
            return output.size() > 0 ? output.toString(StandardCharsets.UTF_8.name()) : "";
        } catch (Exception e) {
            return "";
        } finally {
            inflater.end();
        }
    }

    private byte[] hexToBytes(String value) {
        int size = value.length() / 2;
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private String qrcToEnhancedLrc(String qrc) {
        String content = extractLyricContent(qrc);
        StringBuilder builder = new StringBuilder();
        for (String raw : content.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") || line.startsWith("[by:")) continue;
            Matcher lineMatcher = QRC_LINE.matcher(line);
            if (!lineMatcher.find()) continue;
            long lineStart = parseLong(lineMatcher.group(1));
            long lineDuration = parseLong(lineMatcher.group(2));
            String lineContent = lineMatcher.group(3);
            StringBuilder words = new StringBuilder();
            Matcher wordMatcher = QRC_WORD.matcher(lineContent);
            while (wordMatcher.find()) {
                String word = wordMatcher.group(1);
                if (TextUtils.isEmpty(word)) continue;
                long start = normalizeWordStart(parseLong(wordMatcher.group(2)), lineStart, lineDuration);
                long duration = parseLong(wordMatcher.group(3));
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

    private String extractLyricContent(String qrc) {
        if (TextUtils.isEmpty(qrc)) return "";
        Matcher greedy = Pattern.compile("LyricContent\\s*=\\s*\"([\\s\\S]*)\"\\s*/?>").matcher(qrc);
        if (greedy.find() && !Pattern.compile("\\s+\\w+\\s*=\\s*\"").matcher(greedy.group(1)).find()) return decodeXml(greedy.group(1));
        Matcher normal = Pattern.compile("LyricContent\\s*=\\s*\"([^\"]*)\"").matcher(qrc);
        return decodeXml(normal.find() ? normal.group(1) : qrc);
    }

    private String decodeXml(String text) {
        return (text == null ? "" : text)
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
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
        return text == null ? "" : text.replaceAll("<[^>]+>", "").trim();
    }

    private String encodeBase64(String value) {
        return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private String buildSearchId() {
        long left = (long) (Math.random() * 20) * 18014398509481984L;
        long middle = (long) (Math.random() * 4194304) * 4294967296L;
        long right = System.currentTimeMillis() % 86400000L;
        return String.valueOf(left + middle + right);
    }

    private String formatTime(long timeMs) {
        long minute = timeMs / 60000;
        long second = timeMs % 60000;
        return String.format(Locale.US, "[%02d:%02d.%03d]", minute, second / 1000, second % 1000);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static class Entry {
        private int id;
        private String mid;
        private String name;
        private String artist;
        private String album;
        private int durationSec;
        private int score;
    }

    private static class Lyric {
        private String qrc;
        private String lrc;
        private String trans;
        private String roma;
    }
}
