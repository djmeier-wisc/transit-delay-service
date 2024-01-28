package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.Entity;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.Header;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DelayWriterCronServiceTest {

    @Mock
    private RouteTimestampRepository routeTimestampRepository;

    @Mock
    private GtfsRtFeedService realtimeMetroService;

    @Mock
    private RealtimeResponseConverter adaptor;

    @InjectMocks
    private DelayWriterCronService delayWriterCronService;

    @Test
    public void testDoesWrite() {
        RealtimeTransitResponse transitResponse = new RealtimeTransitResponse();
        Header head = new Header();
        Entity entity = new Entity();
        head.setTimestamp(System.currentTimeMillis() / 1000);
        transitResponse.setHeader(head);
        transitResponse.setEntity(List.of(entity));
        List<RouteTimestamp> routeTimestamps = new ArrayList<>();
        routeTimestamps.add(RouteTimestamp.builder().route("A").build());
        routeTimestamps.add(RouteTimestamp.builder().route("B").build());

        when(realtimeMetroService.getCurrentRunData()).thenReturn(transitResponse);
        when(adaptor.convertFrom(transitResponse)).thenReturn(routeTimestamps);

        delayWriterCronService.getDelayAndWriteToDb();

        verify(routeTimestampRepository, times(1)).writeRouteTimestamps(routeTimestamps);
    }

    @Test
    public void testNoWrite() {
        when(realtimeMetroService.getCurrentRunData()).thenReturn(null);

        delayWriterCronService.getDelayAndWriteToDb();

        verify(routeTimestampRepository, never()).writeRouteTimestamps(anyList());
    }

    @Test
    public void testRemoveDuplicate() {
        RealtimeTransitResponse transitResponse = new RealtimeTransitResponse();
        Header head = new Header();
        Entity entity = new Entity();
        head.setTimestamp(System.currentTimeMillis() / 1000);
        transitResponse.setHeader(head);
        transitResponse.setEntity(List.of(entity));
        List<RouteTimestamp> routeTimestamps = new ArrayList<>();
        routeTimestamps.add(RouteTimestamp.builder().route("A").build());
        routeTimestamps.add(RouteTimestamp.builder().route("A").build());

        when(realtimeMetroService.getCurrentRunData()).thenReturn(transitResponse);
        when(adaptor.convertFrom(transitResponse)).thenReturn(routeTimestamps);

        delayWriterCronService.getDelayAndWriteToDb();

        verify(routeTimestampRepository, times(1)).writeRouteTimestamps(routeTimestamps.subList(0, 1));
    }
}