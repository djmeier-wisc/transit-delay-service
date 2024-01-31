package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.repository.StopTimesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StopService {
    private static final int stopRangeToCheck = 10;
    private final StopTimesRepository stopTimesRepository;

    public List<Integer> getTenNearestStopsFor(Integer tripId, Integer stopId) {
        List<Integer> stopTimes = stopTimesRepository.getStopTimes(tripId, null).map(StopTime::getStop_id).sorted()
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
