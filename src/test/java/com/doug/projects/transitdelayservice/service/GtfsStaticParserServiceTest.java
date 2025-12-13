package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopTime;
import com.doug.projects.transitdelayservice.repository.GtfsStaticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GtfsStaticParserServiceTest {

    // Helper method to convert HH:mm:ss to seconds (for readability)
    private int toSecs(int h, int m, int s) {
        return h * 3600 + m * 60 + s;
    }

    @Test
    void interpolateDelayOneStop() {
        // "05:00:00" -> 18000 secs
        AgencyStopTime data1 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 0, 0)).build();
        List<AgencyStopTime> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);

        // Assuming GtfsStaticParserService is in scope
         GtfsStaticParserService.interpolateDelay(gtfsList);

        // Assert on the integer field
        assertEquals(toSecs(5, 0, 0), data1.getDepartureTimeSecs());
    }

    @Test
    void interpolateDelayTwoStops() {
        // "05:00:00" -> 18000 secs
        AgencyStopTime data1 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 0, 0)).build();
        // "05:01:00" -> 18060 secs
        AgencyStopTime data2 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 1, 0)).build();
        List<AgencyStopTime> gtfsList = new ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);

         GtfsStaticParserService.interpolateDelay(gtfsList);

        assertEquals(toSecs(5, 0, 0), data1.getDepartureTimeSecs());
        assertEquals(toSecs(5, 1, 0), data2.getDepartureTimeSecs());
    }

    @Test
    void interpolateDelayOneNullStop() {
        // "05:00:00" -> 18000 secs
        AgencyStopTime data1 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 0, 0)).build();
        // Null for interpolation
        AgencyStopTime data2 = AgencyStopTime.builder().build();
        // "05:02:00" -> 18120 secs
        AgencyStopTime data3 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 2, 0)).build();
        List<AgencyStopTime> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);

         GtfsStaticParserService.interpolateDelay(gtfsList);

        // Expected data2 value: 18000 + (18120 - 18000) / 2 = 18060 ("05:01:00")
        assertEquals(toSecs(5, 0, 0), data1.getDepartureTimeSecs());
        assertEquals(toSecs(5, 1, 0), data2.getDepartureTimeSecs()); // Interpolated
        assertEquals(toSecs(5, 2, 0), data3.getDepartureTimeSecs());
    }

    @Test
    void interpolateDelayTwoBlanks() {
        // "05:00:00" -> 18000 secs
        AgencyStopTime data1 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 0, 0)).build();
        AgencyStopTime data2 = AgencyStopTime.builder().build(); // Null
        AgencyStopTime data3 = AgencyStopTime.builder().build(); // Null
        // "05:01:30" -> 18090 secs
        AgencyStopTime data4 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 1, 30)).build();
        List<AgencyStopTime> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);
        gtfsList.add(data4);

         GtfsStaticParserService.interpolateDelay(gtfsList);

        // Step: (18090 - 18000) / 3 = 30 seconds
        assertEquals(toSecs(5, 0, 0), data1.getDepartureTimeSecs());
        assertEquals(toSecs(5, 0, 30), data2.getDepartureTimeSecs()); // 18030
        assertEquals(toSecs(5, 1, 0), data3.getDepartureTimeSecs()); // 18060
        assertEquals(toSecs(5, 1, 30), data4.getDepartureTimeSecs()); // 18090
    }

    @Test
    void interpolateDelayNullStartOrEnd() {
        // 1: arr 06:00:00 (21600), dep 05:00:00 (18000)
        AgencyStopTime data1 = AgencyStopTime.builder().arrivalTimeSecs(toSecs(6, 0, 0)).departureTimeSecs(toSecs(5, 0, 0)).build();
        AgencyStopTime data2 = AgencyStopTime.builder().build(); // Null
        // 3: dep 05:01:00 (18060). arr is null, should be interpolated
        AgencyStopTime data3 = AgencyStopTime.builder().departureTimeSecs(toSecs(5, 1, 0)).build();
        AgencyStopTime data4 = AgencyStopTime.builder().build(); // Null
        // 5: arr 06:02:00 (21720), dep 05:02:00 (18120)
        AgencyStopTime data5 = AgencyStopTime.builder().arrivalTimeSecs(toSecs(6, 2, 0)).departureTimeSecs(toSecs(5, 2, 0)).build();

        List<AgencyStopTime> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);
        gtfsList.add(data4);
        gtfsList.add(data5);

         GtfsStaticParserService.interpolateDelay(gtfsList);

        // Departure Step: 30 secs
        assertEquals(toSecs(5, 0, 0), data1.getDepartureTimeSecs());
        assertEquals(toSecs(5, 0, 30), data2.getDepartureTimeSecs());
        assertEquals(toSecs(5, 1, 0), data3.getDepartureTimeSecs());
        assertEquals(toSecs(5, 1, 30), data4.getDepartureTimeSecs());
        assertEquals(toSecs(5, 2, 0), data5.getDepartureTimeSecs());

        // Arrival Step: 30 secs (21720 - 21600) / 4 = 30
        assertEquals(toSecs(6, 0, 0), data1.getArrivalTimeSecs());
        assertEquals(toSecs(6, 0, 30), data2.getArrivalTimeSecs());
        assertEquals(toSecs(6, 1, 0), data3.getArrivalTimeSecs());
        assertEquals(toSecs(6, 1, 30), data4.getArrivalTimeSecs());
        assertEquals(toSecs(6, 2, 0), data5.getArrivalTimeSecs());
    }

    @Test
    void interpolateOver24Hrs() {
        // "25:00:00" -> 90000 secs
        AgencyStopTime data1 = AgencyStopTime.builder().departureTimeSecs(toSecs(25, 0, 0)).build();
        AgencyStopTime data2 = AgencyStopTime.builder().build(); // Null
        // "26:00:00" -> 93600 secs
        AgencyStopTime data3 = AgencyStopTime.builder().departureTimeSecs(toSecs(26, 0, 0)).build();

        List<AgencyStopTime> gtfsList = new java.util.ArrayList<>();
        gtfsList.add(data1);
        gtfsList.add(data2);
        gtfsList.add(data3);

         GtfsStaticParserService.interpolateDelay(gtfsList);

        // Step: (93600 - 90000) / 2 = 1800 secs (30 minutes)
        // Expected data2 value: 90000 + 1800 = 91800 secs (i.e., "25:30:00")
        assertEquals(toSecs(25, 0, 0), data1.getDepartureTimeSecs());
        // Asserting the correct total seconds (91800)
        assertEquals(toSecs(25, 30, 0), data2.getDepartureTimeSecs());
        assertEquals(toSecs(26, 0, 0), data3.getDepartureTimeSecs());
    }

    @Test
    void interpolateDelayEmptyList() {
         GtfsStaticParserService.interpolateDelay(emptyList());
        // No assertion needed, just checking for no exception.
    }
}