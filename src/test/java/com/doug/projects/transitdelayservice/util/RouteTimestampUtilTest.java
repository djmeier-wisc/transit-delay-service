package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusStates;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteTimestampUtilTest {

    @Mock
    private StringUtils stringUtils;

    @InjectMocks
    private RouteTimestampUtil routeTimestampUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetMaxDelayForRouteInMinutes() {
        List<RouteTimestamp> timestampsForRoute =
                Arrays.asList(createRouteTimestamp(1, 10), createRouteTimestamp(2, 20), createRouteTimestamp(3, 30));

        Double result = RouteTimestampUtil.getMaxDelayForRouteInMinutes(timestampsForRoute);

        assertEquals(30, result);
    }

    @Test
    void testPercentOnTime() {
        List<RouteTimestamp> timestampsForRoute =
                Arrays.asList(createRouteTimestamp(1, 60), createRouteTimestamp(2, 120), createRouteTimestamp(3, 180));
        Integer criteria = 2;

        Double result = RouteTimestampUtil.percentOnTime(timestampsForRoute, criteria);

        assertEquals(2.0 / 3.0, result);
    }

    @Test
    void testGetMaxDelayFromBusStatesList() {
        RouteTimestamp routeTimestamp = createRouteTimestamp(1, 10);

        Integer result = RouteTimestampUtil.getMaxDelayFromBusStatesList(routeTimestamp);

        assertEquals(20, result.intValue());
    }

    @Test
    void testExtractBusStates() {
        String stringToParse = "10#stopId#123";

        BusStates result = RouteTimestampUtil.extractBusStates(stringToParse);
        assertEquals(10, result.getDelay().intValue());
        assertEquals("stopId", result.getClosestStopId());
        assertEquals(123, result.getTripId());
    }

    @Test
    void testGetAverageDelayDataForRouteInMinutes() {
        List<RouteTimestamp> timestampsForRoute =
                Arrays.asList(createRouteTimestamp(1, 10), createRouteTimestamp(2, 20), createRouteTimestamp(3, 30));

        Double result = RouteTimestampUtil.getAverageDelayDataForRouteInMinutes(timestampsForRoute);

        assertEquals(.333, result);
    }

    private RouteTimestamp createRouteTimestamp(long timestamp, double averageDelay) {
        RouteTimestamp routeTimestamp = new RouteTimestamp();
        routeTimestamp.setTimestamp(1000L * timestamp); // Use some timestamp value based on the timestamp
        routeTimestamp.setAverageDelay(averageDelay);
        double halfAverageDelay = averageDelay / 2;
        routeTimestamp.setBusStatesList(Arrays.asList("10#stopId#123", "20#stopId#456"));
        return routeTimestamp;
    }
}
