package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.IntStream;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightSixDaysAgo;
import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightTonight;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class MapperService {
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsStaticRepository staticRepo;

    private static Feature pointFeatureWithDelayProperty(GtfsStaticData stop, Map<String, List<BusState>> busStatesMap) {
        List<BusState> busStatesForStop = busStatesMap.get(stop.getId());
        if (busStatesForStop == null) return null;
        Point point = new Point();
        point.setCoordinates(new LngLatAlt(stop.getStopLon(), stop.getStopLat()));
        Feature feature = new Feature();
        feature.setGeometry(point);
        IntStream delay = busStatesForStop.stream()
                .filter(busState -> nonNull(busState.getDelay()))
                .mapToInt(BusState::getDelay);
        OptionalDouble avgDelay = delay.average();
        if (avgDelay.isEmpty()) return null;
        feature.setProperties(Map.of("averageDelay", avgDelay.getAsDouble() / 60));
        feature.setProperties(Map.of("maxDelay", ));
        return feature;
    }

    /**
     * Gets delays for each stop, and maps them to Features with points, having the delay attribute of averageDelay
     *
     * @param feedId
     * @param routeName
     * @return
     */
    public Mono<FeatureCollection> getDelayStopPoints(String feedId, String routeName) {
        Mono<List<BusState>> allBusStates = staticRepo.findAllRoutes(feedId)
                .flatMapMany(gtfsData -> {
                    var routeNames = gtfsData.stream().map(GtfsStaticData::getRouteName).toList();
                    return routeTimestampRepository.getRouteTimestampsMapBy(getMidnightSixDaysAgo(),
                                    getMidnightTonight(),
                                    routeNames,
                                    feedId)
                            .flatMapIterable(m -> m.values()
                                    .stream()
                                    .flatMap(Collection::stream)
                                    .flatMap(a ->
                                            a.getBusStatesCopyList().stream()).toList());
                })
                .collectList();

        Mono<List<GtfsStaticData>> stopData = allBusStates.flatMap(busStates -> {
            List<String> distinctStopIds = busStates.stream().map(BusState::getClosestStopId).distinct().toList();
            return staticRepo.findAllStops(feedId, distinctStopIds).collectList();
        });
        return allBusStates.zipWith(stopData).flatMapIterable(allData -> {
            var stopIdToBusStatesMap = allData.getT1().stream()
                    .filter(s -> nonNull(s.getClosestStopId()))
                    .collect(groupingBy(BusState::getClosestStopId));
            return allData.getT2().stream().map(p -> pointFeatureWithDelayProperty(p, stopIdToBusStatesMap)).toList();
        }).collectList().map(list -> {
            FeatureCollection features = new FeatureCollection();
            features.setFeatures(list);
            return features;
        });
    }
}
