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
    private final GtfsStaticParserService staticService;
    private final AgencyFeedRepository agencyFeedRepository;
    private final GtfsFeedAggregator gtfsFeedAggregator;
    private final GtfsRealtimeParserService rtResponseService;
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    @Value("${doesCronRun}")
    private Boolean doesCronRun;

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
//    @Scheduled(fixedRate = 7, timeUnit = TimeUnit.DAYS)
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

    /**
     * Writes all ACT agency's data to DynamoDb for processing.
     * Done asynchronously to avoid blocking scheduler thread.
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    @Async
    public void writeGtfsRealtimeData() {
        log.info("Starting static data write");
        agencyFeedRepository.getACTStatusAgencyFeeds()
                .stream().map(feed -> //TODO failure case...
                        rtResponseService.convertFromAsync(feed.getId(), feed.getRealTimeUrl())
                                //set timeout to avoid thread starvation, unreliable urls
                                .thenAcceptAsync(routeTimestampRepository::saveAll)
                ).reduce(CompletableFuture::allOf).get().join();
        log.info("Finished static data write");
    }
}
