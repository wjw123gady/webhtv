package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbHeaderViewLayoutTest {

    @Test
    public void headerShowsSmartRecommendationReasonOnCardFocus() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("Smart recommendation row must listen for focus changes",
                source.contains("personalAiRecommendationAdapter.setOnItemFocusListener(this::showAiRecommendationReason);"));
        assertTrue("Smart recommendation focus must update the visible reason text",
                source.contains("private void showAiRecommendationReason(TmdbItem item, boolean focused)"));
        assertTrue("Smart recommendation reason should use the localized preview string",
                source.contains("getString(R.string.ai_recommendation_reason_preview"));
        assertTrue("Smart recommendation reason must be cleared when the row is hidden",
                source.contains("showAiRecommendationReason(null, false);"));
    }

    @Test
    public void headerLayoutPlacesSmartRecommendationReasonBelowSmartRow() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int smartList = layout.indexOf("@+id/tmdbPersonalAiRecommendations");
        int reason = layout.indexOf("@+id/tmdbPersonalAiReason");
        int nextSection = layout.indexOf("@+id/tmdbOmdbRatingsLabel");

        assertTrue("Header layout must contain the smart recommendation row", smartList >= 0);
        assertTrue("Header layout must contain the smart recommendation reason text", reason > smartList);
        assertTrue("Smart recommendation reason should appear before the next section", nextSection > reason);
    }

    @Test
    public void recommendationAdapterDispatchesFocusForAlreadyFocusedCards() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRecommendationAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("Recommendation adapter must dispatch focus when a focused card is rebound",
                source.contains("if (itemView.hasFocus() && focusListener != null) focusListener.onItemFocus(item, true);"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
