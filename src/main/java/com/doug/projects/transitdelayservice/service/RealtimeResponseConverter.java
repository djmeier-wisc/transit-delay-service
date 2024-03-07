package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.Entity;
import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeResponseConverter {
    private final RouteMapperService routeMapperService;

    /**
     * Validate the required fields for Entity mapping
     *
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(Entity entity) {
        return !(entity.getTrip_update() == null || entity.getTrip_update()
                .getTrip() == null || entity.getTrip_update()
                .getTrip()
                .getTrip_id() == null || entity.getTrip_update()
                .getTrip()
                .getRoute_id() == null || CollectionUtils.isEmpty(entity.getTrip_update()
                .getStop_time_update()) || entity.getTrip_update()
                .getStop_time_update()
                .get(0) == null || entity.getTrip_update()
                .getStop_time_update()
                .get(0)
                .getStop_id() == null || entity.getTrip_update()
                .getStop_time_update()
                .get(0)
                .getDeparture() == null || entity.getTrip_update()
                .getStop_time_update()
                .get(0)
                .getDeparture()
                .getDelay() == null);
    }

    /**
     * Gets the average departure delay for a entity
     *
     * @param entity           the entity to get the average delay for
     * @param getAbsoluteValue get the average difference from schedule, or the actual average (including negatives
     * @return the average delay of a trip entity
     */
    private static double getDelays(Entity entity, boolean getAbsoluteValue) {
        return entity.getTrip_update()
                .getStop_time_update()
                .stream()
                .filter(e -> e.getDeparture() != null)
                .map(e -> e.getDeparture()
                        .getDelay())
                .mapToDouble(Integer::doubleValue)
                .map(delay -> {
                    return getAbsoluteValue ? Math.abs(delay) : delay; //apply absolute value to delay average
                })
                .average()
                .orElse(0);
    }

    /**
     * Returns a list of route delays. Note that one value is created per route id, with busStatesList containing the
     * data per run. The timestamp used for this is gathered from the GTFS realtime endpoint response, to prevent data
     * duplication.
     *
     * @param transitResponse the GTFS realtime data to base the calculations on
     * @return a list of routeTimestamps
     */
    List<RouteTimestamp> convertFrom(RealtimeTransitResponse transitResponse) {
        Long timestampFromMetro = transitResponse.getHeader()
                .getTimestamp();
        return transitResponse.getEntity()
                .parallelStream()
                .filter(RealtimeResponseConverter::validateRequiredFields)
                .collect(Collectors.groupingBy(e -> routeMapperService.getFriendlyName(Integer.parseInt(e.getTrip_update()
                        .getTrip()
                        .getRoute_id()))))//group by route id
                .entrySet()
                .stream()
                .map(entry -> {
                    var routeName = entry.getKey();
                    var entityList = entry.getValue();
                    RouteTimestamp rts = new RouteTimestamp();

                    rts.setRoute(routeName);
                    Double averageDelay = entityList.stream()
                            .mapToDouble(entity -> getDelays(entity, true))
                            .average()
                            .orElse(0);
                    rts.setAverageDelay(averageDelay);
                    rts.setTimestamp(timestampFromMetro);
                    rts.setBusStatesList(entityList.stream()
                            .map(e -> {
                                BusState busStates = new BusState();
                                busStates.setTripId(Integer.valueOf(e.getTrip_update()
                                        .getTrip()
                                        .getTrip_id()));
                                busStates.setDelay((int) getDelays(e, false));
                                busStates.setClosestStopId(e.getTrip_update()
                                        .getStop_time_update()
                                        .get(0)
                                        .getStop_id());
                                return busStates.toString();
                            })
                            .toList());
                    return rts;
                })
                .toList();
    }
}
