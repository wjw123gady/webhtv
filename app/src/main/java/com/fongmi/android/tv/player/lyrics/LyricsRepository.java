package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.LyricsSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LyricsRepository {

    public interface Callback {
        void onResult(LyricsResult result);
    }

    private static final String TAG = "lyrics";
    private final LrcLibClient client = new LrcLibClient();
    private final KuwoClient kuwo = new KuwoClient();
    private final QqMusicClient qqMusic = new QqMusicClient();
    private final NeteaseClient netease = new NeteaseClient();
    private final TtmlClient ttml = new TtmlClient();
    private final LyricsMatcher matcher = new LyricsMatcher();

    public void load(LyricsRequest request, Callback callback) {
        load(request, false, callback);
    }

    public void loadPreferWord(LyricsRequest request, Callback callback) {
        load(request, true, callback);
    }

    public void loadPreferWord(LyricsRequest request, boolean forceRefresh, Callback callback) {
        load(request, true, forceRefresh, callback);
    }

    private void load(LyricsRequest request, boolean preferWord, Callback callback) {
        load(request, preferWord, false, callback);
    }

    private void load(LyricsRequest request, boolean preferWord, boolean forceRefresh, Callback callback) {
        Task.execute(() -> {
            LyricsResult result = null;
            try {
                result = loadSync(request, preferWord, forceRefresh);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load failed title=%s error=%s", request.getTitle(), e.getMessage());
            }
            LyricsResult finalResult = result;
            App.post(() -> callback.onResult(finalResult));
        });
    }

    private LyricsResult loadSync(LyricsRequest request, boolean preferWord, boolean forceRefresh) {
        int sourceMode = LyricsSetting.getSourceMode();
        LyricsResult cached = forceRefresh ? null : readCache(request, sourceMode);
        if (cached != null && cached.isValid() && cached.isCacheCurrent() && (!preferWord || isTrustedWord(cached))) return cached;
        LyricsResult remote = cached != null && cached.isValid() && cached.isCacheCurrent() ? cached : null;
        LyricsResult local = readLocal(request);
        if (sourceMode != LyricsSetting.SOURCE_AUTO) return loadSource(request, sourceMode, local);
        if (local != null && local.isValid()) {
            if (!preferWord || local.hasWordTiming()) {
                writeCache(request, sourceMode, local);
                return local;
            }
            if (shouldUseRemote(remote, local)) remote = local;
        }
        LyricsResult qq = qqMusic.find(request);
        if (shouldUseRemote(remote, qq)) remote = qq;
        LyricsResult ttmlResult = ttml.find(request);
        if (shouldUseRemote(remote, ttmlResult)) remote = ttmlResult;
        LyricsResult cloud = netease.find(request);
        if (shouldUseRemote(remote, cloud)) remote = cloud;
        LyricsResult kuwoResult = kuwo.find(request);
        if (shouldUseRemote(remote, kuwoResult)) remote = kuwoResult;
        List<LrcLibClient.Entry> candidates = remote == null || !remote.isValid() ? client.findCandidates(request) : List.of();
        if (remote == null || !remote.isValid()) remote = matcher.best(request, candidates);
        if (remote != null && remote.isValid()) writeCache(request, sourceMode, remote);
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "match title=%s artist=%s candidates=%d result=%s score=%d", request.getTitle(), request.getArtist(), candidates.size(), remote == null ? "none" : remote.getSource(), remote == null ? 0 : remote.getScore());
        return remote;
    }

    private LyricsResult loadSource(LyricsRequest request, int sourceMode, LyricsResult local) {
        LyricsResult result = switch (sourceMode) {
            case LyricsSetting.SOURCE_LOCAL -> local;
            case LyricsSetting.SOURCE_TTML -> ttml.find(request);
            case LyricsSetting.SOURCE_QQ -> qqMusic.find(request);
            case LyricsSetting.SOURCE_NETEASE -> netease.find(request);
            case LyricsSetting.SOURCE_KUWO -> kuwo.find(request);
            case LyricsSetting.SOURCE_LRCLIB -> matcher.best(request, client.findCandidates(request));
            default -> null;
        };
        if (result != null && result.isValid()) writeCache(request, sourceMode, result);
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "match sourceMode=%d title=%s artist=%s result=%s score=%d", sourceMode, request.getTitle(), request.getArtist(), result == null ? "none" : result.getSource(), result == null ? 0 : result.getScore());
        return result;
    }

    private boolean shouldUseRemote(LyricsResult current, LyricsResult remote) {
        if (remote == null || !remote.isValid()) return false;
        if (current == null || !current.isValid()) return true;
        if (remote.hasWordTiming() && !current.hasWordTiming()) return true;
        if (remote.hasWordTiming() && current.hasWordTiming()) {
            int remotePriority = wordPriority(remote);
            int currentPriority = wordPriority(current);
            if (remotePriority > currentPriority && remote.getScore() >= current.getScore() - 15) return true;
            if (remotePriority < currentPriority && remote.getScore() <= current.getScore() + 15) return false;
        }
        return weightedScore(remote) > weightedScore(current) + 10;
    }

    private boolean isTrustedWord(LyricsResult result) {
        return result != null && result.isValid() && result.hasWordTiming() && wordPriority(result) >= 30;
    }

    private int weightedScore(LyricsResult result) {
        return result.getScore() + (result.hasWordTiming() ? wordPriority(result) : 0);
    }

    private int wordPriority(LyricsResult result) {
        String source = result == null || result.getSource() == null ? "" : result.getSource();
        if (source.contains("Local TTML")) return 60;
        if (source.contains("AMLL TTML")) return 50;
        if (source.contains("QQMusic")) return 45;
        if (source.contains("Netease")) return 40;
        if (source.contains("Kuwo")) return 10;
        if (source.contains("Local")) return 30;
        return 0;
    }

    private LyricsResult readCache(LyricsRequest request, int sourceMode) {
        File file = cacheFile(request, sourceMode);
        if (!Path.exists(file)) return null;
        try {
            return App.gson().fromJson(Path.read(file), LyricsResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(LyricsRequest request, int sourceMode, LyricsResult result) {
        try {
            Path.write(cacheFile(request, sourceMode), App.gson().toJson(result).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private LyricsResult readLocal(LyricsRequest request) {
        File source = sourceFile(request.getUrl());
        if (source == null) return null;
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        File parent = source.getParentFile();
        if (parent == null) return null;
        String base = name.substring(0, dot);
        File ttml = findLocal(parent, base, ".ttml", ".TTML");
        if (Path.exists(ttml)) {
            String text = TtmlClient.toEnhancedLrc(Path.read(ttml));
            if (!TextUtils.isEmpty(text) && LyricsParser.hasTimedLine(text)) return new LyricsResult("Local TTML", request.getTitle(), request.getArtist(), request.getAlbum(), text, request.getDurationMs(), true, 104);
        }
        File lrc = findLocal(parent, base, ".lrc", ".LRC");
        if (!Path.exists(lrc)) return null;
        String text = Path.read(lrc);
        if (TextUtils.isEmpty(text)) return null;
        boolean synced = LyricsParser.hasTimedLine(text);
        return new LyricsResult("Local", request.getTitle(), request.getArtist(), request.getAlbum(), text, request.getDurationMs(), synced, 100);
    }

    private File findLocal(File parent, String base, String lower, String upper) {
        File file = new File(parent, base + lower);
        return Path.exists(file) ? file : new File(parent, base + upper);
    }

    private File sourceFile(String url) {
        try {
            if (TextUtils.isEmpty(url)) return null;
            if (url.startsWith("file://")) return new File(Uri.parse(url).getPath());
            if (url.startsWith("/")) return new File(url);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private File cacheFile(LyricsRequest request, int sourceMode) {
        File dir = Path.cache("lyrics");
        if (!dir.exists()) dir.mkdirs();
        String suffix = LyricsSetting.cacheSuffix(sourceMode);
        return new File(dir, request.signature() + (suffix.isEmpty() ? "" : "-" + suffix) + ".json");
    }

    public static int cacheCount() {
        File[] files = cacheDir().listFiles((dir, name) -> name.endsWith(".json"));
        return files == null ? 0 : files.length;
    }

    public static int clearCache() {
        File dir = cacheDir();
        int count = cacheCount();
        Path.clear(dir);
        if (!dir.exists()) dir.mkdirs();
        return count;
    }

    private static File cacheDir() {
        return Path.cache("lyrics");
    }
}
