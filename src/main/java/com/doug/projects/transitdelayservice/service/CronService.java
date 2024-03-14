package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronService {
    private final GtfsStaticParserService gtfsStaticParserService;
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

    //@Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void writeRoutes() {
        agencyFeedRepository.getACTStatusAgencyFeeds().stream()
                .map(feed -> gtfsStaticParserService
                        .writeStaticDataAsync(feed.getStaticUrl(), feed.getId())
                        .completeOnTimeout(null, 60, TimeUnit.SECONDS)) //set timeout to avoid thread starvation
                .reduce(CompletableFuture::allOf)
                .orElse(CompletableFuture.completedFuture(null))
                .join();
        log.info("Finished Routes write");
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void writeRTData() {
        agencyFeedRepository
                .getACTStatusAgencyFeeds()
                .stream()
                .map(agencyFeed -> rtResponseService
                        .convertFromAsync(agencyFeed.getId(), agencyFeed.getRealTimeUrl())
                        .completeOnTimeout(Collections.emptyList(), 60, TimeUnit.SECONDS) //set timeout to avoid thread starvation
                        .thenAcceptAsync(routeTimestampRepository::saveAll))
                .reduce(CompletableFuture::allOf)
                .orElse(CompletableFuture.completedFuture(null))
                .join();

        log.info("Writing RT Data...");
    }
}
