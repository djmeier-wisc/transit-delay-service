package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.DelayObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelayWriterCronService {
    private final DelayObjectRepository delayObjectRepository;
    private final RealtimeMetroService realtimeMetroService;
    private final RealtimeResponseAdaptor adaptor;
    @Value("${doesCronRun}")
    private Boolean doesCronRun;

    private static void removeDuplicates(List<RouteTimestamp> delayObjects) {
        Map<String, List<RouteTimestamp>> delayObjectMap =
                delayObjects.stream().collect(Collectors.groupingBy(RouteTimestamp::getRoute));
        delayObjects.removeIf(obj -> delayObjectMap.get(obj.getRoute()).size() > 1);
    }

    private static boolean nullTransitFields(RealtimeTransitResponse transitResponse) {
        return transitResponse == null || transitResponse.getHeader() == null || transitResponse.getHeader().getTimestamp() == null || transitResponse.getEntity() == null;
    }

    @Scheduled(fixedRate = 300000)
    public void getDelayAndWriteToDb() {
        if (!doesCronRun) return;
        //get realtime delays from metro
        RealtimeTransitResponse transitResponse = realtimeMetroService.getCurrentRunData();
        //remove basic fields for timestamp checking / iteration
        if (nullTransitFields(transitResponse)) {
            log.info("transitResponse:{} data invalid, will not write anything", transitResponse);
            return;
        }
        log.info("Completed realtimeRequest successfully, building model...");
        List<RouteTimestamp> routeTimestamps = adaptor.convertFrom(transitResponse);
        removeDuplicates(routeTimestamps);
        log.info("Built model successfully from {} objects, yielding {} values for database write.",
                transitResponse.getEntity().size(), routeTimestamps.size());
        if (!delayObjectRepository.writeRouteTimestamps(routeTimestamps)) {
            log.error("try again next time... failed writing routeTimestamps :/");
        }
    }


}
