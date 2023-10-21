package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.DelayObjectRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class DelayCalculatorService {
    private DelayObjectRepository delayObjectRepository;
    private RealtimeMetroService realtimeMetroService;
    private RealtimeResponseAdaptor adaptor;

    private static void removeDuplicates(List<RouteTimestamp> delayObjects) {
        Map<String, List<RouteTimestamp>> delayObjectMap =
                delayObjects.stream().collect(Collectors.groupingBy(RouteTimestamp::getRoute));
        delayObjects.removeIf(obj -> delayObjectMap.get(obj.getRoute()).size() > 1);
    }

    @Scheduled(fixedRate = 300000)
    public void getDelayAndWriteToDb() {
        //get realtime delays from metro
        RealtimeTransitResponse transitResponse = realtimeMetroService.getCurrentRunData();
        //remove basic fields for timestamp checking / iteration
        if(nullTransitFields(transitResponse)) {
            log.info("transitResponse:{} data invalid, will not write anything",transitResponse);
            return;
        }
        log.info("Completed realtimeRequest successfully, building model...");
        List<RouteTimestamp> routeTimestamps = adaptor.convertFrom(transitResponse);
        removeDuplicates(routeTimestamps);
        log.info("Built model successfully from {} objects, yielding {} values for database write.",
                transitResponse.getEntity().size(), routeTimestamps.size());
        delayObjectRepository.writeToDb(routeTimestamps);
    }


    private static boolean nullTransitFields(RealtimeTransitResponse transitResponse) {
        return transitResponse == null || transitResponse.getHeader() == null || transitResponse.getHeader().getTimestamp() == null || transitResponse.getEntity() == null;
    }


}
