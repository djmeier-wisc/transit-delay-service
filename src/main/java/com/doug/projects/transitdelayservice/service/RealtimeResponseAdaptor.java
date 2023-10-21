package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusStates;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.Entity;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RealtimeResponseAdaptor {
    private final RouteMapperService routeMapperService;

    /**
     * Validate the required fields for Entity mapping
     *
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

    List<RouteTimestamp> convertFrom(RealtimeTransitResponse transitResponse) {
        int timestampFromMetro = transitResponse.getHeader().getTimestamp();
        return transitResponse.getEntity().parallelStream().filter(RealtimeResponseAdaptor::validateRequiredFields).collect(Collectors.groupingBy(
                e -> {
                    return routeMapperService.getFriendlyName(Integer.parseInt(e.getTrip_update().getTrip().getRoute_id()));
                }
        )).entrySet().stream().map(entry -> {
            var routeName = entry.getKey();
            var entityList = entry.getValue();
            RouteTimestamp rts = new RouteTimestamp();
            rts.setRoute(routeName);
            Double averageDelay =
                    entityList.stream().map(e -> e.getTrip_update().getDelay()).mapToDouble(Integer::doubleValue).average().orElseGet(() -> 0);
            rts.setAverageDelay(averageDelay);
            rts.setTimestamp(timestampFromMetro);
            rts.setBusStatesList(entityList.stream().map(e -> {
                BusStates busStates = new BusStates();
                busStates.setTripId(Integer.valueOf(e.getTrip_update().getTrip().getTrip_id()));
                busStates.setDelay(e.getTrip_update().getDelay());
                busStates.setClosestStopId(e.getTrip_update().getStop_time_update().get(0).getStop_id());
                return busStates.toString();
            }).collect(Collectors.toList()));
            return rts;
        }).collect(Collectors.toList());
    }
}
