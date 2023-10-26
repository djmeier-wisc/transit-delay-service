package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class GetDelayServiceTest {
    @Mock
    private RouteTimestampRepository repository;
    @Spy
    private RouteMapperService routeMapperService;

    private GetDelayService delayService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        routeMapperService.refreshMaps();
        delayService = new GetDelayService(repository, routeMapperService);
    }

    @Test
    public void testGetDelayForWhenRouteProvidedThenReturnLineGraphDataResponse() {
        // Arrange
        Long startTime = 1609459200L;
        Long endTime = 1609545600L;
        Integer units = 7;
        String route = "SampleRoute";
        RouteTimestamp rt1 =
                RouteTimestamp.builder().timestamp(1609459210).route("SampleRoute").averageDelay(1.0).build();
        RouteTimestamp rt2 =
                RouteTimestamp.builder().timestamp(1609459220).route("SampleRoute").averageDelay(1.0).build();
        List<RouteTimestamp> routeTimestamps = Arrays.asList(rt1, rt2);
        when(repository.getRouteTimestampsBy(startTime, endTime, route)).thenReturn(routeTimestamps);

        // Act
        LineGraphDataResponse response = delayService.getDelayFor(startTime, endTime, units, route);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getDatasets().size());
        assertEquals(route, response.getDatasets().get(0).getLineLabel());
    }

    @Test
    public void testGetDelayForWhenNoRouteProvidedThenReturnLineGraphDataResponse() {
        // Arrange
        Long startTime = 0L;
        Long endTime = 10L;
        Integer units = 1;
        RouteTimestamp rt1 = RouteTimestamp.builder().timestamp(5).route("Route1").averageDelay(20d).build();
        RouteTimestamp rt2 = RouteTimestamp.builder().timestamp(10).route("Route1").averageDelay(10d).build();
        Map<String, List<RouteTimestamp>> routeTimestamps = Map.of("Route1", Arrays.asList(rt1,
                rt2));
        when(repository.getRouteTimestampsMapBy(startTime, endTime)).thenReturn(routeTimestamps);

        // Act
        LineGraphDataResponse response = delayService.getDelayFor(startTime, endTime, units, null);

        // Assert
        assertNotNull(response);
        assertEquals((int) units, (int) response.getDatasets().stream().filter(res -> res.getLineLabel().equals(
                "Route1")).mapToLong(r -> r.getData().size()).sum());
        assertEquals(15,
                response.getDatasets().stream().filter(r -> "Route1".equals(r.getLineLabel())).findFirst().get().getData().get(0));
        assertEquals(routeTimestamps.keySet().stream().findAny().get(), response.getDatasets().get(0).getLineLabel());
        assertEquals(units, response.getLabels().size());
    }

    @Test
    public void testGetDelayForWhenStartTimeGreaterThanEndTimeThenThrowIllegalArgumentException() {
        // Arrange
        Long startTime = 1609545600L;
        Long endTime = 1609459200L;
        Integer units = 7;
        String route = "SampleRoute";

        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> {
            delayService.getDelayFor(startTime, endTime, units, route);
        });
    }

    @Test
    public void testGetDelayForWhenTimesAndUnitsNotProvidedThenUseDefaultValues() {
        // Arrange
        String route = "SampleRoute";
        List<RouteTimestamp> routeTimestamps = Arrays.asList(new RouteTimestamp(), new RouteTimestamp());
        when(repository.getRouteTimestampsBy(null, null, route)).thenReturn(routeTimestamps);

        // Act
        LineGraphDataResponse response = delayService.getDelayFor(null, null, null, route);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getDatasets().size());
        assertEquals(7, response.getLabels().size());
        assertEquals(route, response.getDatasets().get(0).getLineLabel());
    }
}