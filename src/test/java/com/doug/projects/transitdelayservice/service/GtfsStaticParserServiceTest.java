package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GtfsStaticParserServiceTest {
    @Mock
    private GtfsStaticRepository gtfsStaticRepository;
    @Mock
    private AgencyFeedRepository agencyFeedRepository;
    @InjectMocks
    private GtfsStaticParserService staticParserService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void interpolateDelayTwoStops() {
        GtfsStaticData data1 = GtfsStaticData.builder().id("").departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().id("").departureTime("05:01:00").build();
        GtfsStaticParserService.interpolateDelay(List.of(data1, data2));
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:01:00", data2.getDepartureTime());
    }

    @Test
    void interpolateDelayOneNullStop() {
        GtfsStaticData data1 = GtfsStaticData.builder().id("").departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().id("").build();
        GtfsStaticData data3 = GtfsStaticData.builder().id("").departureTime("05:02:00").build();
        GtfsStaticParserService.interpolateDelay(List.of(data1, data2, data3));
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:01:00", data2.getDepartureTime());
        assertEquals("05:02:00", data3.getDepartureTime());
    }

    @Test
    void interpolateDelayTwoBlanks() {
        GtfsStaticData data1 = GtfsStaticData.builder().id("").departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().id("").build();
        GtfsStaticData data3 = GtfsStaticData.builder().id("").build();
        GtfsStaticData data4 = GtfsStaticData.builder().id("").departureTime("05:01:30").build();
        GtfsStaticParserService.interpolateDelay(List.of(data1, data2, data3, data4));
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:00:30", data2.getDepartureTime());
        assertEquals("05:01:00", data3.getDepartureTime());
        assertEquals("05:01:30", data4.getDepartureTime());
    }

    @Test
    void interpolateDelayNullStartOrEnd() {

    }

    @Test
    void interpolateDelayEmptyList() {

    }
}