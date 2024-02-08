package com.doug.projects.transitdelayservice.service;

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
import java.util.Objects;
import java.util.Optional;
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
        List<StopTime> stopTimes = stopTimeService.getStopTimesForStop(stopName, route, time);
        if (stopTimes.size() != 1) {
            log.info("Gathered more than one trip ({}) with these criteria: {},{},{},{}", stopTimes, stopName, route,
                    time, searchPeriod);
            return Optional.empty();
        }
        return getAverageDelayForStop(stopTimes.get(0).getTrip_id(), stopTimes.get(0).getStop_id(), searchPeriod);
    }

    /**
     * Gets the average delay of a bus stop for a particular trip. Data is sampled from previous searchPeriod days.
     * Useful if you wanted to predict what time someone should show up to a particular stop to board a particular trip
     *
     * @param stopId the stopId used to calculate delay, for a particular tripId
     * @param tripId the trip to calculate the average delay for, when it is near stopId
     * @param searchPeriod the number of days to search back when getting delay
     * @return -1
     * @apiNote See overloaded class for a more user-friendly way to gather this data involving stopName
     */
    public Optional<Double> getAverageDelayForStop(Integer tripId, Integer stopId, Integer searchPeriod) {
        Optional<Integer> routeIdOptional = tripRepository.getRouteIdFor(tripId);
        if (routeIdOptional.isEmpty()) {
            return Optional.empty();
        }
        String routeFriendlyName = routeMapperService.getFriendlyName(routeIdOptional.get());
        Long startTime = getMidnightTonight() - daysToSeconds(searchPeriod);
        //Get 10 closest stops for this trip. Used because our busStates list only contains the nearest stop, not
        // it's position along the routeShape. geoJson shapes scary :(
        List<Integer> nearStopSet = stopTimeService.getTenNearestStopsFor(tripId, stopId, 10);
        List<Integer> routeTimestampList =
                routeTimestampRepository.getRouteTimestampsBy(startTime, getMidnightTonight(), routeFriendlyName)
                        .stream().flatMap(RouteTimestampUtil::getTimeBusState)
                        .filter(busStates -> Objects.equals(busStates.getTripId(), tripId) &&
                                nearStopSet.contains(busStates.getClosestStopId())) //only this trip & near stop
                        .map(TimeBusState::getDelay).sorted().toList();
        return Optional.of(routeTimestampList.get(routeTimestampList.size() / 2) / 60.0);
    }

    private String getDateFromUTCTimestamp() {
        return "";
    }
}
