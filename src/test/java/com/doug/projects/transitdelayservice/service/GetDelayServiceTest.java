package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.DelayObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class GetDelayServiceTest {
    @Mock
    private DelayObjectRepository repository;
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
    public void testGetDelayForColumnLabels() {
        // Arrange
        Long startTime = 1609459200L;
        Long endTime = 1609545600L;
        Integer units = 7;
        String route = "SampleRoute";
        List<RouteTimestamp> routeTimestamps = Arrays.asList(new RouteTimestamp(), new RouteTimestamp());
        when(repository.getRouteTimestampsBy(startTime, endTime, route)).thenReturn(routeTimestamps);

        // Act
        LineGraphDataResponse response = delayService.getDelayFor(startTime, endTime, units, route);

        // Assert
        List<String> expectedColumnLabels = Arrays.asList("01/01/70 00:00:00 AM", "01/02/70 00:00:00 AM");
        assertEquals(expectedColumnLabels, response.getLabels());
    }

    @Test
    public void testGetDelayForIllegalArgumentException() {
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
    public void testGetDelayForDatasets() {
        // Arrange
        Long startTime = 1609459200L;
        Long endTime = 1609545600L;
        Integer units = 7;
        String route = "SampleRoute";
        List<RouteTimestamp> routeTimestamps = Arrays.asList(new RouteTimestamp(), new RouteTimestamp());
        when(repository.getRouteTimestampsBy(startTime, endTime, route)).thenReturn(routeTimestamps);

        // Act
        LineGraphDataResponse response = delayService.getDelayFor(startTime, endTime, units, route);

        // Assert
        List<LineGraphData> datasets = response.getDatasets();
        assertEquals(2, datasets.size());

        LineGraphData dataset1 = datasets.get(0);
        assertEquals("Line 1", dataset1.getLineLabel());
        assertEquals(Arrays.asList(0.0, 0.0), dataset1.getData());
        assertEquals(false, dataset1.getFill());
        assertEquals("#000000", dataset1.getBorderColor());
        assertEquals(1.0, dataset1.getTension());

        LineGraphData dataset2 = datasets.get(1);
        assertEquals("Line 2", dataset2.getLineLabel());
        assertEquals(Arrays.asList(0.0, 0.0), dataset2.getData());
        assertEquals(false, dataset2.getFill());
        assertEquals("#000000", dataset2.getBorderColor());
        assertEquals(1.0, dataset2.getTension());
    }
}