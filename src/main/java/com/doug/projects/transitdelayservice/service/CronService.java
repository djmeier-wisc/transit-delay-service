package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyFeedRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
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
    @Qualifier("retry")
    private final Executor retryExecutor;
    @Qualifier("dynamoWriting")
    private final Executor dynamoExecutor;
    private final AgencyFeedService agencyFeedService;
    @Value("${doesAgencyCronRun}")
    private Boolean doesAgencyCronRun;
    @Value("${doesRealtimeCronRun}")
    private Boolean doesRealtimeCronRun;

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.DAYS)
    @Transactional
    public void writeFeeds() {
        if (!doesAgencyCronRun)
            return;
        try {
            log.info("Gathering Feeds...");
            List<AgencyFeedDto> newFeeds = gtfsFeedAggregator.gatherRTFeeds();
            log.info("Gathered Feeds. Writing feeds to table...");
            populateTimezoneFromOldFeeds(newFeeds);
            agencyFeedService.deleteAll();
            agencyFeedService.saveAll(newFeeds);
            log.info("Wrote {} feeds successfully.", newFeeds.size());
        } catch (Exception e) {
            log.error("Failed to write gtfs static data", e);
        }
    }

    private void populateTimezoneFromOldFeeds(List<AgencyFeedDto> newFeeds) {
        for (AgencyFeedDto oldFeed : agencyFeedService.getAllAgencyFeeds()) {
            for (AgencyFeedDto newFeed : newFeeds) {
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

        var feeds = agencyFeedService.getAllAgencyFeeds();

        for (AgencyFeedDto feed : feeds) {
            var rtResp = rtResponseService.pollFeed(feed, 60);
            if(rtResp != null) retryOnFailureService.pollStaticFeedIfNeeded(rtResp);
            List<AgencyRouteTimestamp> routeTimestamps = rtResp != null && rtResp.getRouteTimestamps() != null ?
                    rtResp.getRouteTimestamps() :
                    Collections.emptyList();
            routeTimestampRepository.saveAll(routeTimestamps);
        }
        log.info("Finished realtime data write");
    }
}
