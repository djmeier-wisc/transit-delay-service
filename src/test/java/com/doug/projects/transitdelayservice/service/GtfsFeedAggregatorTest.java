package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.config.OpenMobilityDataProperties;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GtfsFeedAggregatorTest {

    @Test
    public void testGatherRTFeedsIncludesHardcoded() throws Exception {
        OpenMobilityDataProperties props = new OpenMobilityDataProperties();
        OpenMobilityDataProperties.HardcodedFeed hf = new OpenMobilityDataProperties.HardcodedFeed();
        hf.setStaticUrl("https://metro.kingcounty.gov/GTFS/google_transit.zip");
        hf.setRtTu("https://s3.amazonaws.com/kcm-alerts-realtime-prod/tripupdates.pb");
        props.getHardcodedFeeds().add(hf);

        GtfsFeedAggregator agg = new GtfsFeedAggregator(props);

        // create an empty CSV with header so the CSV reader returns an empty list
        Path tmp = Files.createTempFile("oms-catalog", ".csv");
        String header = "mdb_source_id,data_type,entity_type,static_reference,urls.direct_download,redirect.id\n";
        Files.writeString(tmp, header);

        // set private fields feedUrl and allowedFeedIds
        setField(agg, "feedUrl", tmp.toUri().toString());
        setField(agg, "allowedFeedIds", Set.of("hardcoded-static-0"));

        List<AgencyFeedDto> feeds = agg.gatherRTFeeds();

        assertNotNull(feeds);
        assertEquals(1, feeds.size(), "Should return one allowed hardcoded RT feed");
        AgencyFeedDto f = feeds.get(0);
        assertEquals("https://s3.amazonaws.com/kcm-alerts-realtime-prod/tripupdates.pb", f.getRealTimeUrl());
        assertEquals("https://metro.kingcounty.gov/GTFS/google_transit.zip", f.getStaticUrl());
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field fld = target.getClass().getDeclaredField(name);
        fld.setAccessible(true);
        fld.set(target, value);
    }
}
