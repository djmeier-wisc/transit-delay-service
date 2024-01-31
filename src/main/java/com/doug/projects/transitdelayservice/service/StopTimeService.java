package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.entity.transit.Stop;
import com.doug.projects.transitdelayservice.repository.StopTimesRepository;
import com.doug.projects.transitdelayservice.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StopTimeService {
    private final StopTimesRepository stopTimesRepository;
    private final TripRepository tripRepository;
    private final StopMapperService stopMapper;
    private final RouteMapperService routeMapper;

    public List<String> getScheduledDepartureTimesForStop(String stopName, String routeName) {
        Set<Integer> stopIds = stopMapper.getStopsByName(stopName).map(Stop::getStop_id).collect(Collectors.toSet());
        List<Integer> routeIds = routeMapper.getRouteIdFor(routeName);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);

        return stopTimesRepository.getStopTimes(stopIds, tripIds).map(StopTime::getDeparture_time).toList();
    }

    public List<StopTime> getStopTimesForStop(String stopName, String routeName, String departureTime) {
        Set<Integer> stopIds = stopMapper.getStopsByName(stopName).map(Stop::getStop_id).collect(Collectors.toSet());
        List<Integer> routeIds = routeMapper.getRouteIdFor(routeName);
        List<Integer> tripIds = tripRepository.getTripIdsFor(routeIds);

        return stopTimesRepository.getStopTimes(stopIds, tripIds)
                .filter(stopTime -> Objects.equals(departureTime, stopTime.getDeparture_time())).toList();
    }
}
