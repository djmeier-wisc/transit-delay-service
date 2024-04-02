package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class RealtimeResponseAdaptorTest {

    private static final Random random = new Random(1234);
    @InjectMocks
    private RealtimeResponseAdaptor realtimeResponseAdaptor;
    @Mock
    private RouteMapperService routeMapperService;

    private static List<StopTimeUpdate> generateStopTimeUpdate(int number) {
        String stopId = "stop" + random.nextInt(10);
        List<StopTimeUpdate> stopTimeUpdates = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            stopTimeUpdates.add(StopTimeUpdate.builder().stop_id(stopId).departure(Departure.builder().delay(random.nextInt(100)).build()).arrival(Arrival.builder().delay(10).build()).build());
        }
        return stopTimeUpdates;
    }

    private static Trip generateTrip() {
        return Trip.builder().trip_id(String.valueOf(random.nextInt(100))).route_id(String.valueOf(random.nextInt(100))).build();
    }

    private static TripUpdate generateTripUpdate() {
        return TripUpdate.builder().trip(generateTrip()).stop_time_update(generateStopTimeUpdate(5)).build();
    }

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void checkProperConversion() {
        RealtimeTransitResponse realtimeTransitResponse = new RealtimeTransitResponse();
        realtimeTransitResponse.setHeader(Header.builder().timestamp(123L).build());
        List<Entity> entityList = new ArrayList<>();
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        realtimeTransitResponse.setEntity(entityList);
        when(routeMapperService.getFriendlyName(anyInt())).thenReturn("routeId");
        List<RouteTimestamp> routeTimestamps = realtimeResponseAdaptor.convertFrom(realtimeTransitResponse);

        assertNotNull(routeTimestamps);
        assertEquals(List.of(RouteTimestamp.builder().route("routeId").timestamp(123L)
                .busStatesList(List.of("40" + "#stop3" +
                "#28", "64#stop0#97")).averageDelay(52.5).build()), routeTimestamps);
    }

    @Test
    public void assertSize() {
        RealtimeTransitResponse realtimeTransitResponse = new RealtimeTransitResponse();
        realtimeTransitResponse.setHeader(Header.builder().timestamp(123L).build());
        List<Entity> entityList = new ArrayList<>();
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        entityList.add(Entity.builder().trip_update(generateTripUpdate()).build());
        realtimeTransitResponse.setEntity(entityList);
        when(routeMapperService.getFriendlyName(anyInt())).thenAnswer(invocation -> invocation.getArgument(0) + "friendlyName");

        List<RouteTimestamp> routeTimestamps = realtimeResponseAdaptor.convertFrom(realtimeTransitResponse);

        assertNotNull(routeTimestamps);
        assertEquals(5, routeTimestamps.size());
    }
}