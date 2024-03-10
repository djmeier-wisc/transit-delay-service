package com.doug.projects.transitdelayservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GtfsRtFeedAggregatorTest {
    private final GtfsRtFeedAggregator rtFeedAggregator = new GtfsRtFeedAggregator();

    @Test
    public void verifySize() {
        //TODO: Remove this...
        ReflectionTestUtils.setField(rtFeedAggregator, "feedUrl", "https://bit.ly/catalogs-csv");
        var feedCount = rtFeedAggregator.gatherRTFeeds();
        System.out.println(feedCount);
    }
}