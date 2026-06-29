package com.fongmi.android.tv.player.lyrics;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsParser {

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private static class Mark {

        private final long time;
        private final int start;
        private final int end;

        private Mark(long time, int start, int end) {
            this.time = time;
            this.start = start;
            this.end = end;
        }
    }

    public static boolean hasTimedLine(String text) {
        return !TextUtils.isEmpty(text) && TIME.matcher(text).find();
    }

    public static List<LyricsLine> parseTimed(String text) {
        List<LyricsLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return lines;
        for (String raw : text.replace("\r", "").split("\n")) {
            Matcher matcher = TIME.matcher(raw);
            List<Mark> marks = new ArrayList<>();
            while (matcher.find()) {
                marks.add(new Mark(parseTime(matcher), matcher.start(), matcher.end()));
            }
            if (marks.isEmpty()) continue;
            addLine(lines, raw, marks);
        }
        lines.sort(Comparator.comparingLong(LyricsLine::getTimeMs));
        return compact(lines);
    }

    public static List<LyricsLine> parsePlain(String text, long durationMs) {
        List<String> texts = new ArrayList<>();
        if (!TextUtils.isEmpty(text)) {
            for (String raw : text.replace("\r", "").split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("[") && line.endsWith("]")) continue;
                texts.add(line);
            }
        }
        List<LyricsLine> lines = new ArrayList<>();
        if (texts.isEmpty()) return lines;
        long step = durationMs > 0 ? Math.max(3000, durationMs / Math.max(1, texts.size())) : 5000;
        for (int i = 0; i < texts.size(); i++) lines.add(new LyricsLine(i * step, texts.get(i)));
        return lines;
    }

    public static int findLine(List<LyricsLine> lines, long positionMs) {
        if (lines == null || lines.isEmpty()) return -1;
        int low = 0;
        int high = lines.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).getTimeMs() <= positionMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    private static long parseTime(Matcher matcher) {
        long minute = parseLong(matcher.group(1));
        long second = parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long millis = 0;
        if (!TextUtils.isEmpty(fraction)) {
            if (fraction.length() == 1) millis = parseLong(fraction) * 100;
            else if (fraction.length() == 2) millis = parseLong(fraction) * 10;
            else millis = parseLong(fraction.substring(0, 3));
        }
        return minute * 60_000 + second * 1000 + millis;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void addLine(List<LyricsLine> lines, String raw, List<Mark> marks) {
        if (isPrefixTimed(raw, marks)) {
            String lyric = raw.substring(marks.get(marks.size() - 1).end).trim();
            if (lyric.isEmpty()) return;
            for (Mark mark : marks) lines.add(new LyricsLine(mark.time, lyric));
            return;
        }
        List<Long> pending = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            Mark mark = marks.get(i);
            int next = i + 1 < marks.size() ? marks.get(i + 1).start : raw.length();
            String lyric = raw.substring(mark.end, next).trim();
            pending.add(mark.time);
            if (lyric.isEmpty()) continue;
            for (long time : pending) lines.add(new LyricsLine(time, lyric));
            pending.clear();
        }
    }

    private static boolean isPrefixTimed(String raw, List<Mark> marks) {
        int cursor = 0;
        for (Mark mark : marks) {
            if (!raw.substring(cursor, mark.start).trim().isEmpty()) return false;
            cursor = mark.end;
        }
        return true;
    }

    private static List<LyricsLine> compact(List<LyricsLine> input) {
        List<LyricsLine> output = new ArrayList<>();
        String last = null;
        long lastTime = -1;
        for (LyricsLine line : input) {
            if (line.getText().equals(last) && line.getTimeMs() == lastTime) continue;
            output.add(line);
            last = line.getText();
            lastTime = line.getTimeMs();
        }
        return output;
    }
}
