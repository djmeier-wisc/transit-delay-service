package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.geojson.Feature;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightSixDaysAgo;
import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightTonight;

@Service
@RequiredArgsConstructor
public class MapperService {
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsStaticRepository staticRepo;

    public Flux<Feature> getDelayFeatureFor(String feedId, String routeName) {
        routeTimestampRepository.getRouteTimestampsBy(getMidnightSixDaysAgo(), getMidnightTonight(), routeName, feedId)
                .collectList()
                .flatMap(timestamps -> {
                    var busStates = timestamps.stream().flatMap(r -> r.getBusStatesCopyList().stream()).toList();
                    var trips = busStates.stream().map(BusState::getTripId).toList();
                    return Tuples.fn2().staticRepo.getStopTimes(feedId, trips).collectList();
                })
                .map(stopTimes -> {

                })
    }
}
