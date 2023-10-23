package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.DelayObjectRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DelayWriterCronServiceTest {

    @Mock
    private DelayObjectRepository delayObjectRepository;

    @Mock
    private RealtimeMetroService realtimeMetroService;

    @Mock
    private RealtimeResponseAdaptor adaptor;

    @InjectMocks
    private DelayWriterCronService delayWriterCronService;

    //@Test
    public void testGetDelayAndWriteToDbWhenTransitResponseIsValidAndWriteToDbIsSuccessfulThenReturnVoid() {
        RealtimeTransitResponse transitResponse = mock(RealtimeTransitResponse.class);
        when(realtimeMetroService.getCurrentRunData()).thenReturn(transitResponse);
        when(adaptor.convertFrom(transitResponse)).thenReturn(Collections.emptyList());
        when(delayObjectRepository.writeRouteTimestamps(Collections.emptyList())).thenReturn(true);

        delayWriterCronService.getDelayAndWriteToDb();

        verify(delayObjectRepository, times(1)).writeRouteTimestamps(Collections.emptyList());
    }

    //@Test
    public void testGetDelayAndWriteToDbWhenTransitResponseIsNullThenNoWriteToDbIsPerformed() {
        when(realtimeMetroService.getCurrentRunData()).thenReturn(null);

        delayWriterCronService.getDelayAndWriteToDb();

        verify(delayObjectRepository, never()).writeRouteTimestamps(anyList());
    }

    //@Test
    public void testGetDelayAndWriteToDbWhenWriteToDbFailsThenRetryWriteToDb() {
        RealtimeTransitResponse transitResponse = mock(RealtimeTransitResponse.class);
        when(realtimeMetroService.getCurrentRunData()).thenReturn(transitResponse);
        when(adaptor.convertFrom(transitResponse)).thenReturn(Collections.emptyList());
        when(delayObjectRepository.writeRouteTimestamps(Collections.emptyList())).thenReturn(false);

        delayWriterCronService.getDelayAndWriteToDb();

        verify(delayObjectRepository, times(1)).writeRouteTimestamps(Collections.emptyList());
    }
}