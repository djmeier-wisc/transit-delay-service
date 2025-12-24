package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronService {
    private final GtfsFeedAggregator gtfsFeedAggregator;
    private final GtfsRealtimeParserService rtResponseService;
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsRetryOnFailureService retryOnFailureService;
    private final AgencyFeedService agencyFeedService;
    private final GtfsStaticParserService gtfsStaticParserService;
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
            var rtResp = rtResponseService.pollFeed(feed);
            if(rtResp != null) retryOnFailureService.pollStaticFeedIfNeeded(rtResp);
            List<AgencyRouteTimestamp> routeTimestamps = rtResp != null && rtResp.getRouteTimestamps() != null ?
                    rtResp.getRouteTimestamps() :
                    Collections.emptyList();
            routeTimestampRepository.saveAll(routeTimestamps, feed.getId());
        }
        log.info("Finished realtime data write");
    }

    //    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.DAYS)
    public void writeGTFSStaticData() {
        for (AgencyFeedDto feed : agencyFeedService.getAllAgencyFeeds()) {
            var staticResult = gtfsStaticParserService.writeGtfsRoutesToDiskAsync(feed, 240).join();
            if (!staticResult.isSuccess()) {
                log.error("Retried reading static feed, but failed. Marking feed as unavailable");
            }
            gtfsStaticParserService.writeGtfsStaticDataToDynamoFromDiskSync(feed);
        }
    }
}
