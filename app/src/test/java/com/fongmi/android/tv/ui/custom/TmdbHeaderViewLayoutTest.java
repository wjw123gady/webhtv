package com.fongmi.android.tv.ui.custom;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbHeaderViewLayoutTest {

    @Test
    public void portraitRecommendationCardsUseReadableTextPanel() throws Exception {
        Path layout = findMainResPath().resolve(Path.of("layout", "adapter_tmdb_recommendation.xml"));
        Element panel = findAndroidId(layout.toFile(), "recommendationTextPanel");

        assertTrue(layout + " is missing @+id/recommendationTextPanel", panel != null);
        assertFalse("recommendationTextPanel must provide a stable background",
                panel.getAttribute("android:background").isEmpty());
    }

    @Test
    public void portraitRecommendationRatingUsesTopEndBadge() throws Exception {
        Path layout = findMainResPath().resolve(Path.of("layout", "adapter_tmdb_recommendation.xml"));
        Element rating = findAndroidId(layout.toFile(), "rating");
        String gravity = rating == null ? "" : rating.getAttribute("android:layout_gravity");

        assertTrue(layout + " is missing @+id/rating", rating != null);
        assertTrue("recommendation rating badge must be pinned to the top edge", gravity.contains("top"));
        assertTrue("recommendation rating badge must be pinned to the end edge", gravity.contains("end"));
        assertFalse("recommendation rating badge must provide its own background",
                rating.getAttribute("android:background").isEmpty());
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }

    private static Element findAndroidId(File file, String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        NodeList nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String id = element.getAttribute("android:id");
            if (id.endsWith("/" + value)) return element;
        }
        return null;
    }
}
