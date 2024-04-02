package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * Used in the event of a failure when reading static data or realtime data.
 * Contains a queue of feeds needing retry
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GtfsRetryOnFailureService {
    private final AgencyFeedRepository feedRepository;
    private final GtfsStaticParserService staticParserService;
    private final GtfsRealtimeParserService realtimeParserService;

    public List<AgencyRouteTimestamp> reCheckFailures(AgencyRealtimeResponse realtimeResponse) {
        AgencyFeed.Status feedStatus = realtimeResponse.getFeedStatus();
        AgencyFeed feed = realtimeResponse.getFeed();
        recheckFeedByStatus(feedStatus, feed);
        if (CollectionUtils.isEmpty(realtimeResponse.getRouteTimestamps())) {
            return Collections.emptyList();
        }
        return realtimeResponse.getRouteTimestamps();
    }

    /**
     * Recursive retry method. Feeds get one retry on "OUTDATED" status
     *
     * @param feedStatus
     * @param feed
     */
    @Async
    private void recheckFeedByStatus(AgencyFeed.Status feedStatus, AgencyFeed feed) {
        switch (feedStatus) {
            case ACTIVE -> {
                //do nothing
            }
            case UNAUTHORIZED, DELETED, UNAVAILABLE -> {
                log.error("UPDATING FEED TO UNAVAILABLE/UNAUTH/DELETED");
                feedRepository.removeAgencyFeed(feed);
                feed.setStatus(String.valueOf(feedStatus));
                feedRepository.writeAgencyFeed(feed);
            }
            case OUTDATED -> {
                var staticResult = staticParserService.writeGtfsRoutesToDiskAsync(feed, 240).join();
                if (!staticResult.isSuccess()) {
                    log.error("Retried reading static feed, but failed. Marking feed as unavailable");
                    recheckFeedByStatus(AgencyFeed.Status.UNAVAILABLE, feed);
                }
                staticParserService.writeGtfsStaticDataToDynamoFromDiskSync(feed);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("Sleeping interrupted for retry. Continuing...");
                }
                var realtimeResult = realtimeParserService.convertFromAsync(feed, 240).join();
                if (realtimeResult.getFeedStatus() != AgencyFeed.Status.ACTIVE) {
                    log.error("Retried reading realtime feed, but was unable to find associated staticData in Dynamo");
                    recheckFeedByStatus(AgencyFeed.Status.UNAVAILABLE, feed);
                }
            }
            case TIMEOUT -> {
                var realtimeResult = realtimeParserService.convertFromAsync(feed, 240).join();
                if (realtimeResult.getFeedStatus() != AgencyFeed.Status.ACTIVE) {
                    log.error("TIMEOUT - marking feed as unavailable");
                    recheckFeedByStatus(AgencyFeed.Status.UNAVAILABLE, feed);
                }
            }
        }
    }
}
