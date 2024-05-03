package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed.Status.ACTIVE;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronService {
    private final AgencyFeedRepository agencyFeedRepository;
    private final GtfsFeedAggregator gtfsFeedAggregator;
    private final GtfsRealtimeParserService rtResponseService;
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsRetryOnFailureService retryOnFailureService;
    @Qualifier("retry")
    private final Executor retryExecutor;
    @Qualifier("dynamoWriting")
    private final Executor dynamoExecutor;
    @Value("${doesAgencyCronRun}")
    private Boolean doesAgencyCronRun;
    @Value("${doesRealtimeCronRun}")
    private Boolean doesRealtimeCronRun;

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.DAYS)
    public void writeFeeds() {
        if (!doesAgencyCronRun)
            return;
        try {
            log.info("Gathering Feeds...");
            var feeds = gtfsFeedAggregator.gatherRTFeeds();
            log.info("Gathered Feeds. Writing feeds to table...");
            agencyFeedRepository.removeAllAgencyFeeds();
            agencyFeedRepository.writeAgencyFeeds(feeds);
            log.info("Wrote feeds successfully.");
        } catch (Exception e) {
            log.error("Failed to write gtfs static data", e);
        }
    }

    /**
     * Writes all ACT agency's data to DynamoDb for processing.
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void writeGtfsRealtimeData() {
        if (!doesRealtimeCronRun)
            return;
        log.info("Starting realtime data write");
        CompletableFuture<?>[] allFutures =
                agencyFeedRepository.getAgencyFeedsByStatus(ACTIVE)
                        .stream()
                        .map(feed ->
                                rtResponseService.convertFromAsync(feed, 60)
                                        .thenApplyAsync(retryOnFailureService::reCheckFailures, retryExecutor)
                                        .thenAcceptAsync(routeTimestampRepository::saveAll, dynamoExecutor))
                        .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(allFutures).join();

        log.info("Finished realtime data write");
    }
}
