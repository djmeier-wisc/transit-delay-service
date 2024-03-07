package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.entity.dynamodb.TimeBusState;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.TripRepository;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.daysToSeconds;
import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightTonight;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripDelayService {
    private final RouteTimestampRepository routeTimestampRepository;
    private final TripRepository tripRepository;
    private final RouteMapperService routeMapperService;
    private final StopTimeService stopTimeService;
    private final StopMapperService stopMapperService;
    private final DelayGraphingService graphingService;

    private static Stream<BusState> getBusStatesAsStream(RouteTimestamp rt) {
        return rt.getBusStatesList()
                .stream()
                .map(RouteTimestampUtil::extractBusStates);
    }

    /**
     * @param stopName
     * @param route        the route friendly name
     * @param time
     * @param searchPeriod
     * @return
     */
    public LineGraphDataResponse getAverageDelayForStop(String stopName, String route, String time,
                                                        Integer searchPeriod) {
        List<StopTime> stopTimes = stopTimeService.getStopTimesForStop(stopName, route, time);
        if (stopTimes.isEmpty()) {
            log.info("Gathered no trips ({}) with these criteria: {},{},{},{}", stopTimes, stopName, route, time,
                    searchPeriod);
            return new LineGraphDataResponse();
        }
        var tripIds = stopTimes.stream()
                .map(StopTime::getTrip_id)
                .toList();
        return getAverageDelayForStop(tripIds, stopTimes.get(0)
                .getStop_id(), searchPeriod, route);
    }

    /**
     * Gets the average delay of a bus stop for a particular trip. Data is sampled from previous searchPeriod days.
     * Useful if you wanted to predict what time someone should show up to a particular stop to board a particular trip
     *
     * @param stopId       the stopId used to calculate delay, for a particular tripId
     * @param tripIds      the tripIds to calculate the average delay for, when it is near stopId
     * @param searchPeriod the number of days to search back when getting delay
     * @param routeName        the routeName to use as the name of the line in the graph
     * @return -1
     * @apiNote See overloaded class for a more user-friendly way to gather this data involving stopName
     */
    public LineGraphDataResponse getAverageDelayForStop(List<Integer> tripIds, Integer stopId, Integer searchPeriod,
                                                        String routeName) {
        List<Integer> routeIds = tripRepository.getRouteIdsFor(tripIds);
        if (routeIds.isEmpty()) {
            return new LineGraphDataResponse();
        }
        List<String> routeFriendlyNames = routeMapperService.getFriendlyNames(routeIds);
        Long startTime = getMidnightTonight() - daysToSeconds(searchPeriod);
        //Get 10 closest stops for this trip. Used because our busStates list only contains the nearest stop, not
        // it's position along the routeShape. geoJson shapes scary :(
        List<Integer> nearestStops = stopTimeService.getTenNearestStopsFor(tripIds, stopId, 20);
        List<TimeBusState> timeBusStateList = routeFriendlyNames.stream()
                .flatMap(routeFriendlyName -> routeTimestampRepository.getRouteTimestampsBy(startTime,
                                getMidnightTonight(), routeFriendlyName)
                        .stream()
                        .flatMap(RouteTimestampUtil::getTimeBusState)
                        .filter(busStates -> tripIds.contains(busStates.getTripId()) && //only this trip & near stop
                                nearestStops.contains(busStates.getClosestStopId())))
                .toList();
        return graphingService.exactTimeBusStateGraph(timeBusStateList, searchPeriod / 2, routeName);
    }
}
