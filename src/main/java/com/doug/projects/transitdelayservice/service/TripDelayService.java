package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.TripRepository;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.daysToSeconds;
import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightTonight;

@Service
@RequiredArgsConstructor
public class TripDelayService {
    private final RouteTimestampRepository routeTimestampRepository;
    private final TripRepository tripRepository;
    private final RouteMapperService routeMapperService;
    private final StopService stopService;
    private final StopTimeService stopTimeService;
    private final StopMapperService stopMapperService;

    private static Stream<BusState> getBusStatesAsStream(RouteTimestamp rt) {
        return rt.getBusStatesList().stream().map(RouteTimestampUtil::extractBusStates);
    }

    /**
     * @param stopName
     * @param route        the route friendly name
     * @param time
     * @param searchPeriod
     * @return
     */
    public Optional<Double> getAverageDelayForStop(String stopName, String route, String time, Integer searchPeriod) {
        List<Integer> routeIds = routeMapperService.getRouteIdFor(route);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);
        List<StopTime> stopTimes = stopTimeService.getStopTimesForStop(stopName, route, time);
        return getAverageDelayForStop(tripIds, stop, searchPeriod)
    }

    /**
     * Gets the average delay of a bus stop
     *
     * @param stopId
     * @param searchPeriod the number of days to search back when getting delay
     * @return -1
     */
    public Optional<Double> getAverageDelayForStop(Integer tripId, Integer stopId, Integer searchPeriod) {
        Optional<Integer> routeIdOptional = tripRepository.getRouteIdFor(tripId);
        if (routeIdOptional.isEmpty()) {
            return Optional.empty();
        }
        String routeFriendlyName = routeMapperService.getFriendlyName(routeIdOptional.get());
        Long startTime = getMidnightTonight() - daysToSeconds(searchPeriod);
        Set<Integer> nearStopSet = new HashSet<>(stopService.getTenNearestStopsFor(tripId, stopId));
        List<BusState> routeTimestampList =
                routeTimestampRepository.getRouteTimestampsBy(startTime, getMidnightTonight(), routeFriendlyName)
                        .parallelStream().flatMap(TripDelayService::getBusStatesAsStream)
                        .filter(busStates -> Objects.equals(busStates.getTripId(), tripId) &&
                                nearStopSet.contains(busStates.getClosestStopId())) //only this trip
                        .toList();
        return Optional.of(-1.);
    }
}
