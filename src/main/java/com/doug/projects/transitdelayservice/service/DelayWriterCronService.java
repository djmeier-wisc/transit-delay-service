package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelayWriterCronService {
    private final RouteTimestampRepository routeTimestampRepository;
    private final GtfsRtFeedService realtimeMetroService;
    private final RealtimeResponseConverter adaptor;
    @Value("${doesCronRun}")
    private Boolean doesCronRun;

    private static List<RouteTimestamp> removeDuplicates(List<RouteTimestamp> routeTimestamps) {
        Set<String> seen = new HashSet<>();
        return routeTimestamps.stream()
                .filter(rt -> seen.add(rt.getRoute()))
                .toList();
    }

    private static boolean nullTransitFields(RealtimeTransitResponse transitResponse) {
        return transitResponse == null || transitResponse.getHeader() == null || transitResponse.getHeader()
                .getTimestamp() == null || transitResponse.getEntity() == null;
    }

    @Scheduled(fixedRate = 300000)
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
            List<RouteTimestamp> uniqueRouteTimestamps = DelayWriterCronService.removeDuplicates(routeTimestamps);
            log.info("Built model successfully from {} objects, yielding {} values for database write.",
                    transitResponse.getEntity()
                    .size(), uniqueRouteTimestamps.size());
            if (!routeTimestampRepository.writeRouteTimestamps(uniqueRouteTimestamps)) {
                throw new RuntimeException("Failed to write route timestamps");
            }
        } catch (Exception e) {
            log.error("Failed to write route timestamps", e);
        }
    }

}
