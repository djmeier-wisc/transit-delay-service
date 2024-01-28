package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.entity.transit.Stop;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.StopTimesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StopTimeService {
    private final StopTimesRepository stopTimesRepository;
    private final RouteTimestampRepository routeTimestampRepository;
    private final StopMapperService mapperService;

    public List<String> getScheduledDepartureTimesForStop(int stopId) {
        return stopTimesRepository.getStopTimes(stopId, Optional.empty()).map(StopTime::getDeparture_time).toList();
    }

    public List<String> getScheduledDepartureTimesForStop(String stopName) {
        List<Integer> stopIds = mapperService.getStopsByName(stopName).map(Stop::getStop_id).toList();
        return stopTimesRepository.getStopTimes(stopIds).map(StopTime::getDeparture_time).toList();
    }
}
