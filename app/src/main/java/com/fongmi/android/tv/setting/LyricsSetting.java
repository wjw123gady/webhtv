package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class LyricsSetting {

    public static final int SOURCE_AUTO = 0;
    public static final int SOURCE_LOCAL = 1;
    public static final int SOURCE_TTML = 2;
    public static final int SOURCE_QQ = 3;
    public static final int SOURCE_NETEASE = 4;
    public static final int SOURCE_KUWO = 5;
    public static final int SOURCE_LRCLIB = 6;

    public static int getSourceMode() {
        return sanitizeSourceMode(Prefers.getInt("lyrics_source_mode", SOURCE_AUTO));
    }

    public static void putSourceMode(int mode) {
        Prefers.put("lyrics_source_mode", sanitizeSourceMode(mode));
    }

    public static int sanitizeSourceMode(int mode) {
        return mode >= SOURCE_AUTO && mode <= SOURCE_LRCLIB ? mode : SOURCE_AUTO;
    }

    public static String cacheSuffix(int mode) {
        return switch (sanitizeSourceMode(mode)) {
            case SOURCE_LOCAL -> "local";
            case SOURCE_TTML -> "ttml";
            case SOURCE_QQ -> "qq";
            case SOURCE_NETEASE -> "netease";
            case SOURCE_KUWO -> "kuwo";
            case SOURCE_LRCLIB -> "lrclib";
            default -> "";
        };
    }
}
