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

import static java.util.Collections.emptyList;
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
    void interpolateDelayOneStop() {
        GtfsStaticData data1 = GtfsStaticData.builder().departureTime("05:00:00").build();
        List<GtfsStaticData> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        GtfsStaticParserService.interpolateDelay(gtfsList);
        assertEquals("05:00:00", data1.getDepartureTime());
    }
    @Test
    void interpolateDelayTwoStops() {
        GtfsStaticData data1 = GtfsStaticData.builder().departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().departureTime("05:01:00").build();
        List<GtfsStaticData> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        GtfsStaticParserService.interpolateDelay(gtfsList);
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:01:00", data2.getDepartureTime());
    }

    @Test
    void interpolateDelayOneNullStop() {
        GtfsStaticData data1 = GtfsStaticData.builder().departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().build();
        GtfsStaticData data3 = GtfsStaticData.builder().departureTime("05:02:00").build();
        List<GtfsStaticData> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);
        GtfsStaticParserService.interpolateDelay(gtfsList);
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:01:00", data2.getDepartureTime());
        assertEquals("05:02:00", data3.getDepartureTime());
    }

    @Test
    void interpolateDelayTwoBlanks() {
        GtfsStaticData data1 = GtfsStaticData.builder().departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().build();
        GtfsStaticData data3 = GtfsStaticData.builder().build();
        GtfsStaticData data4 = GtfsStaticData.builder().departureTime("05:01:30").build();
        List<GtfsStaticData> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);
        gtfsList.add(data4);
        GtfsStaticParserService.interpolateDelay(gtfsList);
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:00:30", data2.getDepartureTime());
        assertEquals("05:01:00", data3.getDepartureTime());
        assertEquals("05:01:30", data4.getDepartureTime());
    }

    @Test
    void interpolateDelayNullStartOrEnd() {
        GtfsStaticData data1 = GtfsStaticData.builder().arrivalTime("06:00:00").departureTime("05:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().build();
        GtfsStaticData data3 = GtfsStaticData.builder().departureTime("05:01:00").build();
        GtfsStaticData data4 = GtfsStaticData.builder().build();
        GtfsStaticData data5 = GtfsStaticData.builder().arrivalTime("06:02:00").departureTime("05:02:00").build();
        List<GtfsStaticData> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);
        gtfsList.add(data4);
        gtfsList.add(data5);
        GtfsStaticParserService.interpolateDelay(gtfsList);
        assertEquals("05:00:00", data1.getDepartureTime());
        assertEquals("05:00:30", data2.getDepartureTime());
        assertEquals("05:01:00", data3.getDepartureTime());
        assertEquals("05:01:30", data4.getDepartureTime());
        assertEquals("05:02:00", data5.getDepartureTime());
        assertEquals("06:00:00", data1.getArrivalTime());
        assertEquals("06:00:30", data2.getArrivalTime());
        assertEquals("06:01:00", data3.getArrivalTime());
        assertEquals("06:01:30", data4.getArrivalTime());
        assertEquals("06:02:00", data5.getArrivalTime());
    }

    @Test
    void interpolateOver24Hrs() {
        GtfsStaticData data1 = GtfsStaticData.builder().departureTime("25:00:00").build();
        GtfsStaticData data2 = GtfsStaticData.builder().build();
        GtfsStaticData data3 = GtfsStaticData.builder().departureTime("26:00:00").build();
        List<GtfsStaticData> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);
        GtfsStaticParserService.interpolateDelay(gtfsList);
        assertEquals("25:00:00", data1.getDepartureTime());
        assertEquals("01:30:00", data2.getDepartureTime());
        assertEquals("26:00:00", data3.getDepartureTime());
    }

    @Test
    void interpolateDelayEmptyList() {
        GtfsStaticParserService.interpolateDelay(emptyList());
    }
}