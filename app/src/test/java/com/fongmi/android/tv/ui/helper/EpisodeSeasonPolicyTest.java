package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeSeasonPolicyTest {

    @Test
    public void sliceBySeasonCounts_slicesOnlyWhenCountsExactlyCoverSourceEpisodes() {
        List<Integer> episodes = List.of(1, 2, 3, 4, 5, 6);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 3, 2, 3);

        assertTrue(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(List.of(4, 5, 6), EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_keepsAllEpisodesWhenTmdbCountsWouldDropSourceEpisodes() {
        List<Integer> episodes = List.of(1, 2, 3, 4, 5, 6, 7);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 3, 2, 3);

        assertFalse(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(episodes, EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }

    @Test
    public void sliceBySeasonCounts_keepsAllEpisodesWhenSeasonCountIsMissing() {
        List<Integer> episodes = List.of(1, 2, 3, 4);
        List<Integer> seasons = List.of(1, 2);
        Map<Integer, Integer> counts = Map.of(1, 2);

        assertFalse(EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), seasons, counts));
        assertEquals(episodes, EpisodeSeasonPolicy.sliceBySeasonCounts(episodes, seasons, counts, 2));
    }
}
