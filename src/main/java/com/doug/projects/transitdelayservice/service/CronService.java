package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronService {
    private final GtfsStaticParserService staticService;
    private final AgencyFeedRepository agencyFeedRepository;
    private final GtfsFeedAggregator gtfsFeedAggregator;
    private final RtResponseMapperService rtResponseService;
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    @Value("${doesCronRun}")
    private Boolean doesCronRun;

    //TODO: Remove, use as reference
    /*@Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void getDelayAndWriteToDb() {
        try {
            if (doesCronRun != null && !doesCronRun)
                return;
            //get realtime delays from metro
            RealtimeTransitResponse transitResponse = realtimeMetroService.getCurrentRunData();
            //remove basic fields for timestamp checking / iteration
            if (nullTransitFields(transitResponse)) {
                log.info("transitResponse:{} data invalid, will not write anything", transitResponse);
                return;
            }
            log.info("Completed realtimeRequest successfully, building model...");
            List<RouteTimestamp> routeTimestamps = adaptor.convertFrom(transitResponse);
            List<RouteTimestamp> uniqueRouteTimestamps = CronService.removeDuplicates(routeTimestamps);
            log.info("Built model successfully from {} objects, yielding {} values for database write.",
                    transitResponse.getEntity()
                            .size(), uniqueRouteTimestamps.size());
            if (!routeTimestampRepository.writeRouteTimestamps(uniqueRouteTimestamps)) {
                throw new RuntimeException("Failed to write route timestamps");
            }
        } catch (Exception e) {
            log.error("Failed to write route timestamps", e);
        }
    }*/

    //@Scheduled(fixedRate = 1, timeUnit = TimeUnit.DAYS)
    public void writeFeeds() {
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
    @Scheduled(fixedRate = 7, timeUnit = TimeUnit.DAYS)
    @Async
    public void writeGtfsStaticData() {
        log.info("Starting static data write");
        agencyFeedRepository.getACTStatusAgencyFeeds()
                .forEach(feed -> staticService
                        .writeGtfsRoutesToDiskAsync(feed.getStaticUrl(), feed.getId(), 60)
                        //set timeout to avoid thread starvation, unreliable urls
                        .thenAcceptAsync((s) -> {
                            if (s.isSuccess())
                                staticService.writeGtfsStaticDataToDynamoFromDiskSync(s.getFeedId());
                            //TODO failure case...
                        }).join()
                );
        log.info("Finished static data write");
    }
}
