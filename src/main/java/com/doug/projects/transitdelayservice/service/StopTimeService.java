package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.entity.transit.Stop;
import com.doug.projects.transitdelayservice.repository.StopTimeRepository;
import com.doug.projects.transitdelayservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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

    private static String removeSeconds(StopTime time) {
        String departureTimes = time.getDeparture_time();
        String[] hrsMinsSeconds = departureTimes.split(":");
        if (hrsMinsSeconds.length < 2) {
            return departureTimes; //we can't split this if the length is incorrect
        }
        String sb = hrsMinsSeconds[0] + ":" + hrsMinsSeconds[1] + ":00";
        return sb;
    }

    public List<String> getScheduledDepartureTimesForStop(String stopName, String routeName) {
        List<Integer> stopIds = stopMapper.getStopsByName(stopName)
                .map(Stop::getStop_id)
                .toList();
        List<Integer> routeIds = routeMapper.getRouteIdFor(routeName);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);

        return stopTimeRepository.getStopTimes(stopIds, tripIds)
                .map(StopTimeService::removeSeconds)
                .distinct()
                .sorted(Comparator.comparing(StopTimeService::parseTime))
                .toList();
    }

    public List<StopTime> getStopTimesForStop(String stopName, String routeName, String departureTimeNoSeconds) {
        List<Integer> stopIds = stopMapper.getStopsByName(stopName)
                .map(Stop::getStop_id)
                .toList();
        List<Integer> routeIds = routeMapper.getRouteIdFor(routeName);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);

        return stopTimeRepository.getStopTimes(stopIds, tripIds)
                .filter(stopTime -> stopTime.getDeparture_time()
                        .startsWith(departureTimeNoSeconds))
                .toList();
    }

    /**
     * Gets the ten nearest stops for a particular trip to a certain stopId, sorted by stopSequence
     *
     * @param tripIds           the tripIds used to search the stopTimeRepo for
     * @param stopId
     * @param stopRangeToCheck
     * @return
     */
    public List<Integer> getTenNearestStopsFor(List<Integer> tripIds, int stopId, int stopRangeToCheck) {
        Map<Integer, List<Integer>> tripIdMap = new HashMap<>();
        for (Integer tripId : tripIds) {
            var stopTimes = stopTimeRepository.getStopTimes(tripId)
                    .sorted(Comparator.comparing(StopTime::getStop_sequence))
                    .map(StopTime::getStop_id)
                    .toList();
            for (int i = 0; i < stopTimes.size(); i++) {
                if (!Objects.equals(stopTimes.get(i), stopId))
                    continue;
                int lowerBound = Math.max(0, i - stopRangeToCheck);
                int upperBound = Math.min(stopTimes.size() - 1, i + stopRangeToCheck);
                tripIdMap.put(tripId, stopTimes.subList(lowerBound, upperBound));
            }
        }
        return tripIdMap.values()
                .stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }
}
