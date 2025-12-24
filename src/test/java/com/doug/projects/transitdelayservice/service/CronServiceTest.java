package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeAnalysisResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.doug.projects.transitdelayservice.entity.dynamodb.Status.ACTIVE;
import static org.mockito.Mockito.*;


class CronServiceTest {
    @InjectMocks
    private CronService cronService;
    @Mock
    private AgencyFeedService agencyFeedService;
    @Mock
    private GtfsFeedAggregator gtfsFeedAggregator;
    @Mock
    private GtfsRealtimeParserService rtResponseService;
    @Mock
    private AgencyRouteTimestampRepository routeTimestampRepository;
    @Mock
    private GtfsRetryOnFailureService retryOnFailureService;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private static List<AgencyFeedDto> getAgencyFeedList() {
        return List.of(getAgencyFeedActive());
    }

    private static AgencyFeedDto getAgencyFeedActive() {
        return AgencyFeedDto.builder().id(getFeedId()).name("Test Agency").state("WI").status(ACTIVE).build();
    }

    private static String getFeedId() {
        return "1";
    }

    private static AgencyRealtimeAnalysisResponse getResponse() {
        List<AgencyRouteTimestamp> routeTimestamps = List.of(new AgencyRouteTimestamp());
        return AgencyRealtimeAnalysisResponse.builder().feed(getAgencyFeedActive()).routeTimestamps(routeTimestamps).build();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void writeFeeds() {
        ReflectionTestUtils.setField(cronService, "doesAgencyCronRun", true);
        when(gtfsFeedAggregator.gatherRTFeeds()).thenReturn(getAgencyFeedList());
        cronService.writeFeeds();
        verify(agencyFeedService).saveAll(eq(getAgencyFeedList()));
    }

    @Test
    void writeAllTypesDuringRealtimeCheck() {
        ReflectionTestUtils.setField(cronService, "doesRealtimeCronRun", true);
        when(agencyFeedService.getAllAgencyFeeds())
                .thenReturn(getAgencyFeedList());
        when(rtResponseService.pollFeed(eq(getAgencyFeedActive())))
                .thenReturn(getResponse());
        when(retryOnFailureService.updateFeedStatus(eq(getResponse()))).thenReturn(List.of(new AgencyRouteTimestamp()));
        cronService.writeGtfsRealtimeData();
        verify(agencyFeedService, times(1)).getAllAgencyFeeds();
        verify(rtResponseService, times(1)).pollFeed(eq(getAgencyFeedActive()));
        verify(routeTimestampRepository, times(1)).saveAll(eq(List.of(new AgencyRouteTimestamp())), anyString());
    }

    @Test
    void doesNotWriteWhenBooleanFalse() {
        ReflectionTestUtils.setField(cronService, "doesAgencyCronRun", false);
        cronService.writeFeeds();
        verifyNoInteractions(agencyFeedService, gtfsFeedAggregator, rtResponseService, routeTimestampRepository, retryOnFailureService);
    }

    @Test
    void doesNotWriteRTWhenBooleanFalse() {
        ReflectionTestUtils.setField(cronService, "doesRealtimeCronRun", false);
        cronService.writeGtfsRealtimeData();
        verifyNoInteractions(agencyFeedService, gtfsFeedAggregator, rtResponseService, routeTimestampRepository, retryOnFailureService);
    }
}