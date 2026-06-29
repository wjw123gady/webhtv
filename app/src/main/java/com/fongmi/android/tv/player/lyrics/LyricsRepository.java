package com.fongmi.android.tv.player.lyrics;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
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
    private final LyricsMatcher matcher = new LyricsMatcher();

    public void load(LyricsRequest request, Callback callback) {
        Task.execute(() -> {
            LyricsResult result = null;
            try {
                result = loadSync(request);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "load failed title=%s error=%s", request.getTitle(), e.getMessage());
            }
            LyricsResult finalResult = result;
            App.post(() -> callback.onResult(finalResult));
        });
    }

    private LyricsResult loadSync(LyricsRequest request) {
        LyricsResult cached = readCache(request);
        if (cached != null && cached.isValid()) return cached;
        LyricsResult local = readLocal(request);
        if (local != null && local.isValid()) {
            writeCache(request, local);
            return local;
        }
        List<LrcLibClient.Entry> candidates = client.findCandidates(request);
        LyricsResult remote = matcher.best(request, candidates);
        if (remote != null && remote.isValid()) writeCache(request, remote);
        if (SpiderDebug.isEnabled()) SpiderDebug.log(TAG, "match title=%s artist=%s candidates=%d result=%s score=%d", request.getTitle(), request.getArtist(), candidates.size(), remote == null ? "none" : remote.getSource(), remote == null ? 0 : remote.getScore());
        return remote;
    }

    private LyricsResult readCache(LyricsRequest request) {
        File file = cacheFile(request);
        if (!Path.exists(file)) return null;
        try {
            return App.gson().fromJson(Path.read(file), LyricsResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(LyricsRequest request, LyricsResult result) {
        try {
            Path.write(cacheFile(request), App.gson().toJson(result).getBytes(StandardCharsets.UTF_8));
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
        File lrc = new File(parent, name.substring(0, dot) + ".lrc");
        if (!Path.exists(lrc)) lrc = new File(parent, name.substring(0, dot) + ".LRC");
        if (!Path.exists(lrc)) return null;
        String text = Path.read(lrc);
        if (TextUtils.isEmpty(text)) return null;
        boolean synced = LyricsParser.hasTimedLine(text);
        return new LyricsResult("Local", request.getTitle(), request.getArtist(), request.getAlbum(), text, request.getDurationMs(), synced, 100);
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

    private File cacheFile(LyricsRequest request) {
        File dir = Path.cache("lyrics");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, request.signature() + ".json");
    }
}
