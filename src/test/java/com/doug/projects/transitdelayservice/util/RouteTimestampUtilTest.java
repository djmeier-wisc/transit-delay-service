package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteTimestampUtilTest {

    @Test
    void testGetMaxDelayForRouteInMinutes() {
        List<AgencyRouteTimestamp> timestampsForRoute =
                List.of(createRouteTimestamp(1), createRouteTimestamp(2), createRouteTimestamp(3));

        Double result = RouteTimestampUtil.maxDelayInMinutes(timestampsForRoute);

        assertEquals(2, result);
    }

    @Test
    void testPercentOnTime() {
        List<AgencyRouteTimestamp> timestampsForRoute =
                List.of(createRouteTimestamp(1), createRouteTimestamp(2), createRouteTimestamp(3));

        Double result = RouteTimestampUtil.percentOnTime(timestampsForRoute, 1, 1);

        assertEquals(50.0, result);
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
        List<AgencyRouteTimestamp> timestampsForRoute =
                List.of(createRouteTimestamp(1), createRouteTimestamp(2), createRouteTimestamp(3));

        Double result = RouteTimestampUtil.medianDelayInMinutes(timestampsForRoute);

        assertEquals(2, result);
    }

    private AgencyRouteTimestamp createRouteTimestamp(long timestamp) {
        AgencyRouteTimestamp routeTimestamp = new AgencyRouteTimestamp();
        routeTimestamp.setTimestamp(1000L * timestamp); // Use some timestamp value based on the timestamp
        routeTimestamp.setBusStatesList(Arrays.asList("60#stopId#123", "120#stopId#456"));
        return routeTimestamp;
    }
}
