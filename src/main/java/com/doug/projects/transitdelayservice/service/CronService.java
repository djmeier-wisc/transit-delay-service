package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronService {
    private final AgencyFeedRepository agencyFeedRepository;
    private final GtfsFeedAggregator gtfsFeedAggregator;
    private final GtfsRealtimeParserService rtResponseService;
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsRetryOnFailureService retryOnFailureService;
    @Value("${doesCronRun}")
    private Boolean doesCronRun;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.DAYS)
    public void writeFeeds() {
        if (!doesCronRun)
            return;
        try {
            log.info("Gathering Feeds...");
            var feeds = gtfsFeedAggregator.gatherRTFeeds();
            log.info("Gathered Feeds. Writing feeds to table...");
            agencyFeedRepository.writeAgencyFeeds(feeds);
            log.info("Wrote feeds successfully.");
        } catch (Exception e) {
            log.error("Failed to write gtfs static data", e);
        }
    }

    /**
     * Writes all ACT agency's data to DynamoDb for processing.
     * Done asynchronously to avoid blocking scheduler thread.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    @Async
    public void writeGtfsRealtimeData() {
        if (!doesCronRun)
            return;
        log.info("Starting realtime data write");
        CompletableFuture<?>[] allFutures =
                agencyFeedRepository.getACTStatusAgencyFeeds()
                        .stream().map(feed -> rtResponseService.convertFromAsync(feed, 60)
                                .thenApply(retryOnFailureService::reCheckFailures)
                                .thenAccept(routeTimestampRepository::saveAll))
                        .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(allFutures).join();

        log.info("Finished realtime data write");
    }
}
