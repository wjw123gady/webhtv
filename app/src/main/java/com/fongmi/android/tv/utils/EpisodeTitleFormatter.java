package com.fongmi.android.tv.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeTitleFormatter {

    private static final Pattern FILE_SIZE = Pattern.compile("(?i)([\\[\\(（【]\\s*)?(\\d+(?:\\.\\d{1,2})?)\\s*(B|KB|MB|M|GB|G|TB|T|PB)(?:\\s*([\\]\\)）】])|(?=$|[\\s._-]))");

    private EpisodeTitleFormatter() {
    }

    public static String formatTmdbTitle(int number, String tmdbTitle) {
        if (!isEmpty(tmdbTitle)) return number > 0 ? number + ". " + tmdbTitle : tmdbTitle;
        if (number > 0) return "第" + number + "集";
        return "";
    }

    public static String withSourceFileSize(String sourceName, String title, boolean includeFileSize) {
        if (isEmpty(title) || !includeFileSize) return isEmpty(title) ? "" : title;
        String fileSize = extractFileSize(sourceName);
        if (isEmpty(fileSize) || containsFileSize(title)) return title;
        return fileSize + " " + title;
    }

    public static String extractFileSize(String value) {
        if (isEmpty(value)) return "";
        Matcher matcher = FILE_SIZE.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (isEmpty(token)) continue;
            boolean hasOpen = !isEmpty(matcher.group(1));
            boolean hasClose = !isEmpty(matcher.group(4));
            if (hasOpen != hasClose) continue;
            return token.trim().replaceAll("\\s+", "");
        }
        return "";
    }

    public static boolean containsFileSize(String value) {
        return !isEmpty(extractFileSize(value));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
