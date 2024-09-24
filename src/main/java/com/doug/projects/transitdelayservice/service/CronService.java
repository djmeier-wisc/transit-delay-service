package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
    @SchedulerLock(name = "feedLock", lockAtMostFor = "P29DT23H", lockAtLeastFor = "P29DT23H")
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
            log.info("Wrote feeds successfully.");
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
//    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
//    @SchedulerLock(name = "realtimeLock", lockAtMostFor = "10m", lockAtLeastFor = "4m")
    public void writeGtfsRealtimeData() {
        if (!doesRealtimeCronRun)
            return;
        log.info("Starting realtime data write");
        CompletableFuture<?>[] allFutures =
                agencyFeedRepository.getAgencyFeedsByStatus(ACTIVE, UNAVAILABLE, TIMEOUT, OUTDATED)
                        .stream()
                        .map(feed ->
                                rtResponseService.convertFromAsync(feed, 60)
                                        .thenApplyAsync(retryOnFailureService::updateFeedStatus, retryExecutor)
                                        .thenAcceptAsync(routeTimestampRepository::saveAll, dynamoExecutor))
                        .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(allFutures)
                .completeOnTimeout(null, 10, TimeUnit.MINUTES) //lets not miss more than two realtime writes of other feeds, if one is lagging
                .join();

        log.info("Finished realtime data write");
    }

    /**
     * Attempts to poll all realtime feeds, except those which we are not authorized for. Writes realtime data to db, if it is available.
     */
    @Scheduled(fixedDelay = 7, timeUnit = TimeUnit.DAYS)
//    @SchedulerLock(name = "staticLock", lockAtMostFor = "P6DT23H", lockAtLeastFor = "P6DT23H")
    public void refreshOutdatedFeeds() {
        if (!doesRealtimeCronRun)
            return;
        log.info("Starting realtime data write, with static data polling");
        CompletableFuture<?>[] allFutures =
                agencyFeedRepository.getAgencyFeedsByStatus(OUTDATED, UNAVAILABLE)
                        .stream()
                        .map(feed ->
                                rtResponseService.convertFromAsync(feed, 60)
                                        .thenApplyAsync(retryOnFailureService::pollStaticFeedIfNeeded, retryExecutor)
                                        .thenAcceptAsync(routeTimestampRepository::saveAll, dynamoExecutor))
                        .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(allFutures).join();

        log.info("Finished realtime data write, with static data polling");
    }
}
