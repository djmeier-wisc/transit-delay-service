package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeAnalysisResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.Status;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

import static com.doug.projects.transitdelayservice.entity.dynamodb.Status.*;

/**
 * Used in the event of a failure when reading static data or realtime data.
 * Contains a queue of feeds needing retry
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GtfsRetryOnFailureService {
    private final GtfsStaticParserService staticParserService;
    private final GtfsRealtimeParserService realtimeParserService;
    private final AgencyFeedRepository agencyFeedRepository;

    public List<AgencyRouteTimestamp> pollStaticFeedIfNeeded(AgencyRealtimeAnalysisResponse realtimeResponse) {
        Status feedStatus = realtimeResponse.getFeedStatus();
        AgencyFeedDto feed = realtimeResponse.getFeed();
        recheckFeedByStatus(feedStatus, feed);
        if (CollectionUtils.isEmpty(realtimeResponse.getRouteTimestamps())) {
            return Collections.emptyList();
        }
        return realtimeResponse.getRouteTimestamps();
    }

    public List<AgencyRouteTimestamp> updateFeedStatus(AgencyRealtimeAnalysisResponse realtimeResponse) {
        Status feedStatus = realtimeResponse.getFeedStatus();
        AgencyFeedDto feed = realtimeResponse.getFeed();
        updateFeedToStatus(feed, feedStatus);
        if (CollectionUtils.isEmpty(realtimeResponse.getRouteTimestamps())) {
            return Collections.emptyList();
        }
        return realtimeResponse.getRouteTimestamps();
    }

    private static void sleepFor(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleeping interrupted for retry. Continuing...");
        }
    }

    /**
     * Recursive retry method. Feeds get one retry on "OUTDATED" status
     *
     * @param feedStatus the (presumably) failed status of the feed. Unless the feed is outdated or ACTIVE, we write the failure status
     * @param feed the feed to check against. If the status is some kind of failure, it will be used to remove the failed feed.
     */
    private void recheckFeedByStatus(Status feedStatus, AgencyFeedDto feed) {
        switch (feedStatus) {
            case ACTIVE -> {
                if (!feed.getStatus().equals(ACTIVE.toString())) {
                    updateFeedToStatus(feed, ACTIVE);
                }
            }
            case UNAUTHORIZED, DELETED -> {
                updateFeedToStatus(feed, feedStatus);
            }
            case OUTDATED -> {
                var staticResult = staticParserService.writeGtfsRoutesToDiskAsync(feed, 240).join();
                if (!staticResult.isSuccess()) {
                    log.error("Retried reading static feed, but failed. Marking feed as unavailable");
                    updateFeedToStatus(feed, UNAVAILABLE);
                }
                staticParserService.writeGtfsStaticDataToDynamoFromDiskSync(feed);
                sleepFor(5);
                var realtimeResult = realtimeParserService.pollFeed(feed);
                if (realtimeResult.getFeedStatus() != Status.ACTIVE) {
                    log.error("Retried reading realtime feed, but was unable to find associated staticData in Dynamo");
                    updateFeedToStatus(feed, realtimeResult.getFeedStatus());
                }
            }
            case TIMEOUT, UNAVAILABLE -> {
                sleepFor(5);
                var realtimeResult = realtimeParserService.pollFeed(feed);
                if (realtimeResult.getFeedStatus() != Status.ACTIVE) {
                    log.error("TIMEOUT - marking feed as unavailable");
                    updateFeedToStatus(feed, TIMEOUT);
                }
            }
        }
    }

    private void updateFeedToStatus(AgencyFeedDto newFeed, Status newStatus) {
        log.error("Updating feed {} to {}", newFeed.getId(), newStatus);
        agencyFeedRepository.updateStatusById(newStatus,newFeed.getId());
    }
}
