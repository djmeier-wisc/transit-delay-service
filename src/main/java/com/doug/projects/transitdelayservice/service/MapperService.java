package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.geojson.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.*;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

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
        var delay = busStatesForStop.stream()
                .filter(busState -> nonNull(busState.getDelay()))
                .mapToInt(BusState::getDelay)
                .toArray();
        OptionalDouble avgDelay = Arrays.stream(delay).average();
        if (avgDelay.isEmpty()) return null;
        feature.setProperties(Map.of("averageDelay", avgDelay.getAsDouble() / 60));
        feature.setProperties(Map.of("maxDelay", Arrays.stream(delay).max()));
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
        Mono<List<BusState>> allBusStates = routeTimestampRepository.getRouteTimestampsMapBy(getMidnightSixDaysAgo(),
                        getMidnightTonight(),
                        List.of(routeName),
                        feedId)
                .flatMapIterable(m -> m.values()
                        .stream()
                        .flatMap(Collection::stream)
                        .flatMap(a -> a.getBusStatesCopyList().stream())
                        .toList())
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

    public Mono<FeatureCollection> getDelayLines(String feedId, String routeName) {
        return routeTimestampRepository.getRouteTimestampsMapBy(getMidnightOneMonthAgo(),
                        getMidnightTonight(),
                        List.of(routeName),
                        feedId)
                .flatMapIterable(m -> m.values()
                        .stream()
                        .flatMap(Collection::stream)
                        .toList())
                .collectList()
                .zipWhen(routeTimestamps -> groupStaticData(feedId, routeTimestamps),
                        ((routeTimestamps, map) -> {
                            var stopTimesByTripId = map.get(GtfsStaticData.TYPE.STOPTIME).stream().collect(groupingBy(GtfsStaticData::getTripId));
                            var stopsByStopId = map.get(GtfsStaticData.TYPE.STOP).stream().collect(toMap(GtfsStaticData::getId, Function.identity()));
                            routeTimestamps.sort(comparing(AgencyRouteTimestamp::getTimestamp));
                            var busStates = routeTimestamps.stream().flatMap(l -> l.getBusStatesCopyList().stream()).toList();
                            Map<LngLatAlt, Map<LngLatAlt, Double>> delayMapping = new HashMap<>();

                            for (String tripId : stopTimesByTripId.keySet()) {
                                List<BusState> busStatesForTripId = busStates.stream().filter(busState -> busState.getTripId().equals(tripId)).toList();
                                List<GtfsStaticData> stopTimesForTripId = stopTimesByTripId.get(tripId);
                                stopTimesForTripId.sort(comparing(GtfsStaticData::getStopSequence, naturalOrder()));
                                if (busStatesForTripId == null) continue;
                                Map<String, Integer> stopIdToSequence = stopTimesForTripId.stream().collect(toMap(GtfsStaticData::getStopId, GtfsStaticData::getStopSequence));
                                for (int busStateIndex = 0; busStateIndex < busStatesForTripId.size() - 1; busStateIndex++) {
                                    Double busStateDelay = Double.valueOf(busStatesForTripId.get(busStateIndex).getDelay());
                                    Integer firstStopSequence = stopIdToSequence.get(busStatesForTripId.get(busStateIndex).getClosestStopId());
                                    Integer secondStopSequence = stopIdToSequence.get(busStatesForTripId.get(busStateIndex + 1).getClosestStopId());
                                    //this should be considered a new run, either on a new day or a repeat trip, since it has finished its route and restarted.
                                    if (secondStopSequence <= firstStopSequence) continue;
                                    List<LngLatAlt> stopPositions = new ArrayList<>();
                                    for (int stopTimeIndex = firstStopSequence; stopTimeIndex < secondStopSequence; stopTimeIndex++) {
                                        GtfsStaticData stopTime = stopTimesForTripId.get(stopTimeIndex);
                                        GtfsStaticData stop = stopsByStopId.get(stopTime.getStopId());
                                        stopPositions.add(new LngLatAlt(stop.getStopLon(), stop.getStopLat()));
                                    }
                                    for (int i = 0; i < stopPositions.size() - 1; i++) {
                                        var first = stopPositions.get(i);
                                        var second = stopPositions.get(i + 1);
                                        delayMapping.putIfAbsent(first, new HashMap<>());
                                        var currMappedDelay = delayMapping.get(first).getOrDefault(second, busStateDelay);
                                        delayMapping.get(first).put(second, (currMappedDelay + busStateDelay) / 2.);
                                    }
                                }
                            }
                            List<Feature> featureList = new ArrayList<>();
                            delayMapping.forEach((from, toDelay) -> {
                                toDelay.forEach((to, delay) -> {
                                    Feature feature = new Feature();
                                    LineString lineString = new LineString();
                                    lineString.setCoordinates(List.of(from, to));
                                    feature.setGeometry(lineString);
                                    feature.setProperty("averageDelay", delay / 60.);
                                    featureList.add(feature);
                                });
                            });
                            FeatureCollection featureCollection = new FeatureCollection();
                            featureCollection.setFeatures(featureList);
                            return featureCollection;
                        }));

    }

    private Mono<Map<GtfsStaticData.TYPE, List<GtfsStaticData>>> groupStaticData(String feedId, List<AgencyRouteTimestamp> routeTimestamps) {
        var busStates = routeTimestamps.stream().flatMap(s -> s.getBusStatesCopyList().stream()).toList();
        var tripIds = busStates.stream().map(BusState::getTripId).distinct().toList();
        return staticRepo.findStopsAndStopTimes(feedId, tripIds).collect(Collectors.groupingBy(GtfsStaticData::getType));
    }
}
