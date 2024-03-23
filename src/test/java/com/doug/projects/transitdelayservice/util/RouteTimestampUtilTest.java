package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteTimestampUtilTest {

    @Test
    void testGetMaxDelayForRouteInMinutes() {
        List<RouteTimestamp> timestampsForRoute =
                Arrays.asList(createRouteTimestamp(1, 10), createRouteTimestamp(2, 20), createRouteTimestamp(3, 30));

        Double result = RouteTimestampUtil.maxDelayInMinutes(timestampsForRoute);

        assertEquals(2, result);
    }

    @Test
    void testPercentOnTime() {
        List<RouteTimestamp> timestampsForRoute =
                Arrays.asList(createRouteTimestamp(1, 60), createRouteTimestamp(2, 120), createRouteTimestamp(3, 180));

        Double result = RouteTimestampUtil.percentOnTime(timestampsForRoute, 1, 1);

        assertEquals(50.0, result);
    }

    @Test
    void testGetMaxDelayFromBusStatesList() {
        RouteTimestamp routeTimestamp = createRouteTimestamp(1, 10);

        Integer result = RouteTimestampUtil.getMaxDelayFromBusStatesList(routeTimestamp);

        assertEquals(120, result.intValue());
    }

    @Test
    void testExtractBusStates() {
        String stringToParse = "10#stopId#123";

        BusState result = BusState.fromString(stringToParse);
        assertEquals(10, result.getDelay().intValue());
        assertEquals("stopId", result.getClosestStopId());
        assertEquals("123", result.getTripId());
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
        routeTimestamp.setBusStatesList(Arrays.asList("60#stopId#123", "120#stopId#456"));
        return routeTimestamp;
    }
}
