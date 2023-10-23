package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RealtimeResponseAdaptorTest {

    @Mock
    private RouteMapperService routeMapperService;

    @InjectMocks
    private RealtimeResponseAdaptor realtimeResponseAdaptor;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    //@Test
    public void testConvertFromWhenTransitResponseNotNullAndValidThenReturnListOfRouteTimestamps() {
        // Arrange
        RealtimeTransitResponse transitResponse = new RealtimeTransitResponse();
        // Set up the transitResponse object with valid data
        // Set up the routeMapperService mock to return expected values

        // Act
        List<RouteTimestamp> result = realtimeResponseAdaptor.convertFrom(transitResponse);

        // Assert
        // Verify that the result is as expected
        // assertEquals(expectedResult, result); // Uncomment this line and replace expectedResult with the expected result
    }

    //@Test
    public void testConvertFromWhenTransitResponseNullThenReturnEmptyList() {
        // Arrange
        RealtimeTransitResponse transitResponse = null;

        // Act
        List<RouteTimestamp> result = realtimeResponseAdaptor.convertFrom(transitResponse);

        // Assert
        assertTrue(result.isEmpty());
    }

    //@Test
    public void testConvertFromWhenTransitResponseNotNullButNoValidDataThenReturnEmptyList() {
        // Arrange
        RealtimeTransitResponse transitResponse = new RealtimeTransitResponse();
        // Set up the transitResponse object with invalid data
        // Set up the routeMapperService mock to return expected values

        // Act
        List<RouteTimestamp> result = realtimeResponseAdaptor.convertFrom(transitResponse);

        // Assert
        assertTrue(result.isEmpty());
    }
}