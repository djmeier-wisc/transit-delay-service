package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.entity.transit.Stop;
import com.doug.projects.transitdelayservice.repository.StopTimeRepository;
import com.doug.projects.transitdelayservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StopTimeService {
    private final StopTimeRepository stopTimeRepository;
    private final TripRepository tripRepository;
    private final StopMapperService stopMapper;
    private final RouteMapperService routeMapper;

    private static long parseTime(String time) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return sdf.parse(time)
                    .getTime();
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing time", e);
        }
    }

    public List<String> getScheduledDepartureTimesForStop(String stopName, String routeName) {
        List<Integer> stopIds = stopMapper.getStopsByName(stopName)
                .map(Stop::getStop_id)
                .toList();
        List<Integer> routeIds = routeMapper.getRouteIdFor(routeName);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);

        return stopTimeRepository.getStopTimes(stopIds, tripIds)
                .map(StopTime::getDeparture_time)
                .distinct()
                .sorted(Comparator.comparing(StopTimeService::parseTime))
                .toList();
    }

    public List<StopTime> getStopTimesForStop(String stopName, String routeName, String departureTime) {
        List<Integer> stopIds = stopMapper.getStopsByName(stopName)
                .map(Stop::getStop_id)
                .toList();
        List<Integer> routeIds = routeMapper.getRouteIdFor(routeName);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);

        return stopTimeRepository.getStopTimes(stopIds, tripIds)
                .filter(stopTime -> Objects.equals(departureTime, stopTime.getDeparture_time()))
                .toList();
    }

    /**
     * Gets the ten nearest stops for a particular trip to a certain stopId, sorted by stopSequence
     *
     * @param tripId           the tripId used to search the
     * @param stopId
     * @param stopRangeToCheck
     * @return
     */
    public List<Integer> getTenNearestStopsFor(int tripId, int stopId, int stopRangeToCheck) {
        List<Integer> stopTimes = stopTimeRepository.getStopTimes(tripId)
                .sorted(Comparator.comparing(StopTime::getStop_sequence))
                .map(StopTime::getStop_id)
                .collect(Collectors.toList());
        for (int i = 0; i < stopTimes.size(); i++) {
            if (!Objects.equals(stopTimes.get(i), stopId))
                continue;
            int lowerBound = Math.min(0, i - stopRangeToCheck);
            int upperBound = Math.max(stopTimes.size() - 1, i + stopRangeToCheck);
            return stopTimes.subList(lowerBound, upperBound);
        }
        return Collections.emptyList();
    }
}
