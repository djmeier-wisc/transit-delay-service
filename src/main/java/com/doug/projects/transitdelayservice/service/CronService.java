package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed.Status.*;

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
            var newFeeds = gtfsFeedAggregator.gatherRTFeeds();
            log.info("Gathered Feeds. Writing feeds to table...");
            populateTimezoneFromOldFeeds(newFeeds);
            agencyFeedRepository.removeAllAgencyFeeds();
            agencyFeedRepository.writeAgencyFeeds(newFeeds);
            log.info("Wrote {} feeds successfully.", newFeeds.size());
        } catch (Exception e) {
            log.error("Failed to write gtfs static data", e);
        }
    }

    private void populateTimezoneFromOldFeeds(List<AgencyFeed> newFeeds) {
        for (AgencyFeed oldFeed : agencyFeedRepository.getAllAgencyFeeds()) {
            for (AgencyFeed newFeed : newFeeds) {
                if (newFeed.getId()
                        .equals(oldFeed.getId())) {
                    newFeed.setTimezone(oldFeed.getTimezone());
                }
            }
        }
    }

    /**
     * Attempts to poll all realtime feeds, except those which we are not authorized for. Writes realtime data to db, if it is available.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void writeGtfsRealtimeData() {
        if (!doesRealtimeCronRun)
            return;
        log.info("Starting realtime data write");
        agencyFeedRepository.getAgencyFeedsByStatusFlux(ACTIVE, UNAVAILABLE, TIMEOUT, OUTDATED)
                .flatMap(feed -> rtResponseService.convertFromAsync(feed, 60))
                .flatMapIterable(retryOnFailureService::updateFeedStatus)
                .buffer(25)
                .doOnNext(routeTimestampRepository::saveAll)
                .then()
                .block();

        log.info("Finished realtime data write");
    }

    /**
     * Attempts to poll all realtime feeds, except those which we are not authorized for. Writes realtime data to db, if it is available.
     */
    @Scheduled(fixedDelay = 7, timeUnit = TimeUnit.DAYS)
    public void refreshOutdatedFeeds() {
        if (!doesRealtimeCronRun)
            return;
        log.info("Starting realtime data write, with static data polling");
        CompletableFuture<?>[] allFutures =
                agencyFeedRepository.getAgencyFeedsByStatus(OUTDATED, UNAVAILABLE)
                        .stream()
                        .map(feed ->
                                rtResponseService.convertFromAsync(feed, 60).toFuture()
                                        .thenApplyAsync(retryOnFailureService::pollStaticFeedIfNeeded, retryExecutor)
                                        .thenAcceptAsync(routeTimestampRepository::saveAll, dynamoExecutor))
                        .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(allFutures).join();

        log.info("Finished realtime data write, with static data polling");
    }
}
