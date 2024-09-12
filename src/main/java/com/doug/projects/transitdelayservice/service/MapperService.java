package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.MapOptions;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.transit.ShapeProperties;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geojson.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.*;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapperService {
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsStaticRepository staticRepo;
    private final AgencyFeedRepository agencyFeedRepository;

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

    public static List<LngLatAlt> divideShape(List<GtfsStaticData> gtfsStaticData, LngLatAlt from, LngLatAlt to) {
        gtfsStaticData = new ArrayList<>(gtfsStaticData);
        gtfsStaticData.sort(Comparator.comparingInt(GtfsStaticData::getSequence));

        // Find the closest shape points for 'from' and 'to'
        int fromIndex = getClosestPointIndex(from, gtfsStaticData, true);
        int toIndex = getClosestPointIndex(to, gtfsStaticData, false);

        if (fromIndex < 0 || toIndex < 0) {
            return emptyList(); // Return an empty list if any point is not found
        }

        if (fromIndex > toIndex) {
            // If fromShape comes after toIndex in the sequence, swap them
            int temp = fromIndex;
            fromIndex = toIndex;
            toIndex = temp;
        }

        return gtfsStaticData.subList(fromIndex, toIndex + 1).stream().map(MapperService::toLngLatAlt).toList();
    }

    private static LngLatAlt toLngLatAlt(GtfsStaticData staticData) {
        LngLatAlt lngLatAlt = new LngLatAlt();
        lngLatAlt.setLatitude(staticData.getStopLat());
        lngLatAlt.setLongitude(staticData.getStopLon());
        return lngLatAlt;
    }

    /**
     * Gets the closest point index.
     *
     * @param point           the point we search shapes against
     * @param shapes          a list of gtfs shape data, used to compare distance
     * @param findLowestIndex if multiple distances are equal, whether to return the lowest or highest one
     * @return the index of the shape closes to point
     */
    private static int getClosestPointIndex(LngLatAlt point, List<GtfsStaticData> shapes, boolean findLowestIndex) {
        double minDistance = Double.MAX_VALUE;
        int closestPoint = -1;
        if (findLowestIndex) { //record the lowest index, provided there are two equal ones
            for (int i = 0; i < shapes.size(); i++) {
                GtfsStaticData shape = shapes.get(i);
                double distance = haversineDistance(point.getLatitude(), point.getLongitude(), shape.getStopLat(), shape.getStopLon());
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = i;
                }
            }
        } else {
            for (int i = shapes.size() - 1; i >= 0; i--) {
                GtfsStaticData shape = shapes.get(i);
                double distance = haversineDistance(point.getLatitude(), point.getLongitude(), shape.getStopLat(), shape.getStopLon());
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = i;
                }
            }
        }

        return closestPoint;
    }

    /**
     * Haversine distance algorithm, which does not account for height.
     *
     * @return the distance between the two points, as a double
     */
    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Earth radius in kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Maps delays from one stop (stored as a lat and long), to another (also stored as a lat and long). Then, maps that data to the average delay between those two stops.
     *
     * @param routeTimestamps
     * @param map
     * @return
     */
    private static @NotNull Map<LngLatAlt, Map<LngLatAlt, ShapeProperties>> getStopDelayMapping(List<AgencyRouteTimestamp> routeTimestamps, Map<GtfsStaticData.TYPE, List<GtfsStaticData>> map) {
        var stopTimesByTripId = map.getOrDefault(GtfsStaticData.TYPE.STOPTIME, emptyList())
                .stream()
                .collect(groupingBy(GtfsStaticData::getTripId));
        var stopsByStopId = map.getOrDefault(GtfsStaticData.TYPE.STOP, emptyList())
                .stream()
                .collect(toMap(GtfsStaticData::getId, Function.identity()));
        var tripsByTripId = map.getOrDefault(GtfsStaticData.TYPE.TRIP, emptyList())
                .stream()
                .collect(toMap(GtfsStaticData::getTripId, Function.identity()));
        var busStatesByTripId = routeTimestamps.stream()
                .sorted(comparing(AgencyRouteTimestamp::getTimestamp))
                .flatMap(l -> l.getBusStatesCopyList()
                        .stream())
                .collect(groupingBy(BusState::getTripId));
        Map<LngLatAlt, Map<LngLatAlt, ShapeProperties>> delayMapping = new HashMap<>();
        for (String tripId : stopTimesByTripId.keySet()) {
            if (!tripsByTripId.containsKey(tripId)) continue;
            String shapeId = tripsByTripId.get(tripId).getShapeId();
            List<BusState> busStatesForTripId = busStatesByTripId.getOrDefault(tripId, emptyList());
            List<GtfsStaticData> stopTimesForTripId = stopTimesByTripId.getOrDefault(tripId, emptyList());
            stopTimesForTripId.sort(comparing(GtfsStaticData::getSequence, naturalOrder()));
            Map<String, Integer> stopIdToSequence = stopTimesForTripId.stream()
                    .collect(toMap(GtfsStaticData::getStopId, GtfsStaticData::getSequence, (a, b) -> a));
            for (int busStateIndex = 0; busStateIndex < busStatesForTripId.size() - 1; busStateIndex++) {
                Integer fromDelay = busStatesForTripId.get(busStateIndex).getDelay();
                if (fromDelay == null) fromDelay = 0;
                Integer toDelay = busStatesForTripId.get(busStateIndex + 1).getDelay();
                if (toDelay == null) toDelay = 0;
                Integer fromStopSeq = stopIdToSequence.get(busStatesForTripId.get(busStateIndex)
                        .getClosestStopId());
                Integer toStopSeq = stopIdToSequence.get(busStatesForTripId.get(busStateIndex + 1)
                        .getClosestStopId());
                //this should be considered a new run, either on a new day or a repeat trip, since it has finished
                // its route and restarted.
                if (toStopSeq == null || fromStopSeq == null || toStopSeq <= fromStopSeq)
                    continue;
                LngLatAlt[] stopPositions = getStopPositions(fromStopSeq, toStopSeq, stopTimesForTripId, stopsByStopId);
                double[] interpolatedDelays = interpolate(fromDelay, toDelay, toStopSeq - fromStopSeq);
                for (int i = 0; i < stopPositions.length - 1; i++) {
                    var fromStop = stopPositions[i];
                    var toStop = stopPositions[i + 1];
                    double currDelay = interpolatedDelays[i];
                    var currDelayAndShape = delayMapping
                            .computeIfAbsent(fromStop, k -> new HashMap<>()) //build new HashMap if none are present for "from"
                            .computeIfAbsent(toStop, k -> ShapeProperties.builder()
                                    .shapeId(shapeId)
                                    .delay(currDelay)
                                    .build());
                    currDelayAndShape.setDelay((currDelayAndShape.getDelay() + fromDelay) / 2.);
                }
            }
        }
        return delayMapping;
    }

    /***
     * Interpolating method
     * @param start start of the interval
     * @param end end of the interval
     * @param count count of output interpolated numbers
     * @return array of interpolated number with specified count
     */
    public static double[] interpolate(double start, double end, int count) {
        if (count < 2) {
            return new double[]{start, end};
        }
        double[] array = new double[count + 1];
        for (int i = 0; i <= count; ++i) {
            array[i] = start + i * (end - start) / count;
        }
        return array;
    }

    private static @NotNull LngLatAlt[] getStopPositions(Integer firstStopSequence,
                                                         Integer secondStopSequence,
                                                         List<GtfsStaticData> stopTimesForTripId,
                                                         Map<String, GtfsStaticData> stopsByStopId) {
        LngLatAlt[] stopPositions = new LngLatAlt[secondStopSequence - firstStopSequence];
        for (int stopTimeIndex = firstStopSequence; stopTimeIndex < secondStopSequence; stopTimeIndex++) {
            GtfsStaticData stopTime = stopTimesForTripId.get(stopTimeIndex);
            GtfsStaticData stop = stopsByStopId.get(stopTime.getStopId());
            int pos = stopTimeIndex - firstStopSequence;
            stopPositions[pos] = new LngLatAlt(stop.getStopLon(), stop.getStopLat());
        }
        return stopPositions;
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
                })
                .collectList()
                .map(this::getFeatureCollection);
    }

    //    @Cacheable(value = DELAY_LINES_CACHE)
    public Mono<FeatureCollection> getDelayLines(String feedId, MapOptions mapOptions) {
        if (StringUtils.isBlank(mapOptions.getRouteName()) || StringUtils.isBlank(feedId)) {
            log.error("Failed either due to empty feedId or empty routeName");
            return Mono.just(new FeatureCollection());
        }
        //this looks weird, but it was the easiest way to zip together a single timeZone with many
        Flux<String> agencyTimezone = agencyFeedRepository.getAgencyFeedById(feedId, true).map(f -> f.getTimezone() == null ? "" : f.getTimezone()).cache().repeat();
        return Flux.zip(routeTimestampRepository.getRouteTimestampsBy(getMidnightDaysAgo(mapOptions.getSearchPeriod()),
                        getMidnightTonight(),
                        List.of(mapOptions.getRouteName()),
                        feedId), agencyTimezone)
                .filter(routeTimestamp -> {
                    var date = Instant.ofEpochSecond(routeTimestamp.getT1().getTimestamp());
                    if (StringUtils.isEmpty(routeTimestamp.getT2())) {
                        return true;
                    }
                    var sampledHour = date.atZone(ZoneId.of(routeTimestamp.getT2())).getHour();
                    var sampledDay = date.atZone(ZoneId.of(routeTimestamp.getT2())).getDayOfWeek();
                    return sampledHour >= mapOptions.getHourStarted() &&
                            sampledHour <= mapOptions.getHourEnded() &&
                            mapOptions.getDaysSelected().contains(sampledDay.getValue());
                })
                .map(Tuple2::getT1)
                .collectList()
                .zipWhen(routeTimestamps -> groupStaticData(feedId, routeTimestamps),
                        ((routeTimestamps, map) -> getFeatureCollection(getFeatureList(map, getStopDelayMapping(routeTimestamps, map)))))
                .cache();
    }

    private @NotNull FeatureCollection getFeatureCollection(List<Feature> map) {
        FeatureCollection featureCollection = new FeatureCollection();
        featureCollection.setFeatures(map);
        return featureCollection;
    }

    private @NotNull List<Feature> getFeatureList(Map<GtfsStaticData.TYPE, List<GtfsStaticData>> map, Map<LngLatAlt,
            Map<LngLatAlt, ShapeProperties>> delayMapping) {
        var shapesByShapeId = map.getOrDefault(GtfsStaticData.TYPE.SHAPE, emptyList())
                .stream()
                .collect(groupingBy(GtfsStaticData::getShapeIdFromId));
        List<Feature> featureList = new ArrayList<>();
        delayMapping.forEach((from, toDelay) -> {
            toDelay.forEach((to, delayAndShape) -> {
                Feature feature = new Feature();
                LineString lineString = new LineString();
                List<GtfsStaticData> shapeData = shapesByShapeId.getOrDefault(delayAndShape.getShapeId(), emptyList());
                lineString.setCoordinates(divideShape(shapeData, from, to));
                feature.setGeometry(lineString);
                feature.setProperty("averageDelay", delayAndShape.getDelay() / 60.);
                featureList.add(feature);
            });
        });
        return featureList;
    }

    private Mono<Map<GtfsStaticData.TYPE, List<GtfsStaticData>>> groupStaticData(String feedId, List<AgencyRouteTimestamp> routeTimestamps) {
        var busStates = routeTimestamps.stream().flatMap(s -> s.getBusStatesCopyList().stream()).toList();
        var tripIds = busStates.stream().map(BusState::getTripId).distinct().toList();
        return staticRepo.findStaticDataFor(feedId, tripIds)
                .collect(Collectors.groupingBy(GtfsStaticData::getType));
    }
}
