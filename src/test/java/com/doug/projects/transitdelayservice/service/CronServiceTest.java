package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed.Status.*;
import static org.mockito.Mockito.*;


class CronServiceTest {
    @InjectMocks
    private CronService cronService;
    @Mock
    private AgencyFeedRepository agencyFeedRepository;
    @Mock
    private GtfsFeedAggregator gtfsFeedAggregator;
    @Mock
    private GtfsRealtimeParserService rtResponseService;
    @Mock
    private AgencyRouteTimestampRepository routeTimestampRepository;
    @Mock
    private GtfsRetryOnFailureService retryOnFailureService;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private static List<AgencyFeed> getAgencyFeedList() {
        return List.of(getAgencyFeedActive());
    }

    private static AgencyFeed getAgencyFeedActive() {
        return AgencyFeed.builder().id(getFeedId()).name("Test Agency").state("WI").status(String.valueOf(ACTIVE)).build();
    }

    private static String getFeedId() {
        return "1";
    }

    private static AgencyRealtimeResponse getResponse() {
        return AgencyRealtimeResponse.builder().feed(getAgencyFeedActive()).routeTimestamps(Collections.emptyList()).build();
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
        verify(agencyFeedRepository, times(1)).removeAllAgencyFeeds();
        verify(agencyFeedRepository).writeAgencyFeeds(eq(getAgencyFeedList()));
    }

    @Test
    void refreshFeed() {
        ReflectionTestUtils.setField(cronService, "doesRealtimeCronRun", true);
        when(agencyFeedRepository.getAgencyFeedsByStatus(OUTDATED, UNAVAILABLE))
                .thenReturn(getAgencyFeedList());
        when(rtResponseService.convertFromAsync(eq(getAgencyFeedActive()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(getResponse()));
        ReflectionTestUtils.setField(cronService, "retryExecutor", executor);
        ReflectionTestUtils.setField(cronService, "dynamoExecutor", executor);
        cronService.refreshOutdatedFeeds();
        verify(agencyFeedRepository, times(1)).getAgencyFeedsByStatus(OUTDATED, UNAVAILABLE);
        verify(rtResponseService, times(1)).convertFromAsync(eq(getAgencyFeedActive()), anyInt());
        verify(retryOnFailureService, times(1)).pollStaticFeedIfNeeded(eq(getResponse()));
        verify(routeTimestampRepository, times(1)).saveAll(eq(Collections.emptyList()));
    }

    @Test
    void writeAllTypesDuringRealtimeCheck() {
        ReflectionTestUtils.setField(cronService, "doesRealtimeCronRun", true);
        when(agencyFeedRepository.getAgencyFeedsByStatus(ACTIVE, UNAVAILABLE, TIMEOUT, OUTDATED))
                .thenReturn(getAgencyFeedList());
        when(rtResponseService.convertFromAsync(eq(getAgencyFeedActive()), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(getResponse()));
        ReflectionTestUtils.setField(cronService, "retryExecutor", executor);
        ReflectionTestUtils.setField(cronService, "dynamoExecutor", executor);
        cronService.writeGtfsRealtimeData();
        verify(agencyFeedRepository, times(1)).getAgencyFeedsByStatus(ACTIVE, UNAVAILABLE, TIMEOUT, OUTDATED);
        verify(rtResponseService, times(1)).convertFromAsync(eq(getAgencyFeedActive()), anyInt());
        verify(retryOnFailureService, times(1)).updateFeedStatus(any());
        verify(routeTimestampRepository, times(1)).saveAll(eq(Collections.emptyList()));
    }

    @Test
    void doesNotWriteWhenBooleanFalse() {
        ReflectionTestUtils.setField(cronService, "doesAgencyCronRun", false);
        cronService.writeFeeds();
        verifyNoInteractions(agencyFeedRepository, gtfsFeedAggregator, rtResponseService, routeTimestampRepository, retryOnFailureService);
    }

    @Test
    void doesNotWriteRTWhenBooleanFalse() {
        ReflectionTestUtils.setField(cronService, "doesRealtimeCronRun", false);
        cronService.writeGtfsRealtimeData();
        verifyNoInteractions(agencyFeedRepository, gtfsFeedAggregator, rtResponseService, routeTimestampRepository, retryOnFailureService);
    }
}