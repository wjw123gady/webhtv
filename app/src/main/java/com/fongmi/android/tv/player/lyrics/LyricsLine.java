package com.fongmi.android.tv.player.lyrics;

public class LyricsLine {

    private final long timeMs;
    private final String text;

    public LyricsLine(long timeMs, String text) {
        this.timeMs = Math.max(0, timeMs);
        this.text = text == null ? "" : text.trim();
    }

    public long getTimeMs() {
        return timeMs;
    }

    public String getText() {
        return text;
    }
}
