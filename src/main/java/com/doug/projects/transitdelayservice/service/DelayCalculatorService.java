package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.DelayObject;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.Entity;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import com.doug.projects.transitdelayservice.repository.DelayObjectRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class DelayCalculatorService {
    private DelayObjectRepository delayObjectRepository;
    private RealtimeMetroService realtimeMetroService;
    private RouteMapperService routeMapperService;

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
        int timestampFromMetro = transitResponse.getHeader().getTimestamp();

        List<DelayObject> delayObjects = transitResponse.getEntity().parallelStream().filter(DelayCalculatorService::validateRequiredFields).map(entity -> {
            String routeFriendlyName =
                    routeMapperService.getFriendlyName(Integer.parseInt(entity.getTrip_update().getTrip().getRoute_id()));
            String runNumber = entity.getTrip_update().getTrip().getTrip_id();
            DelayObject currDelayOfRoute = new DelayObject();
            StringBuilder builder = new StringBuilder();
            builder.append(timestampFromMetro)
                    .append(":")
                    .append(routeFriendlyName).append(":").append(runNumber);
            currDelayOfRoute.setId(builder.toString());
            currDelayOfRoute.setMetroTimestamp((long) timestampFromMetro);
            currDelayOfRoute.setRoute(routeFriendlyName);
            currDelayOfRoute.setDelay(entity.getTrip_update().getStop_time_update().get(0).getDeparture().getDelay());
            currDelayOfRoute.setStopId(entity.getTrip_update().getStop_time_update().get(0).getStop_id());
            currDelayOfRoute.setRunNumber(runNumber);
            return currDelayOfRoute;
        }).collect(Collectors.toList());

        Map<String,List<DelayObject>> delayObjectMap =
                delayObjects.stream().collect(Collectors.groupingBy(DelayObject::getId));
        delayObjects.removeIf(obj->delayObjectMap.get(obj.getId()).size()>1);
        log.info("Built model successfully from {} objects, yielding {} values for database write.",
                transitResponse.getEntity().size(), delayObjects.size());
        delayObjectRepository.writeToDb(delayObjects);
    }

    /**
     * Validate the required fields for Entity mapping
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(Entity entity) {
        return !(entity.getTrip_update() == null ||
                entity.getTrip_update().getTrip() == null ||
                entity.getTrip_update().getTrip().getTrip_id() == null ||
                entity.getTrip_update().getTrip().getRoute_id() == null ||
                CollectionUtils.isEmpty(entity.getTrip_update().getStop_time_update()) ||
                entity.getTrip_update().getStop_time_update().get(0) == null ||
                entity.getTrip_update().getStop_time_update().get(0).getStop_id() == null ||
                entity.getTrip_update().getStop_time_update().get(0).getDeparture() == null ||
                entity.getTrip_update().getStop_time_update().get(0).getDeparture().getDelay() == null);
    }

    private static boolean nullTransitFields(RealtimeTransitResponse transitResponse) {
        return transitResponse == null || transitResponse.getHeader() == null || transitResponse.getHeader().getTimestamp() == null || transitResponse.getEntity() == null;
    }


}
