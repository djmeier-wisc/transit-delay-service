package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.transit.DelayAndShapeId;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.geojson.Point;
import org.geojson.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.util.List;
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

    public static List<LngLatAlt> divideShape(List<GtfsStaticData> gtfsStaticData, LngLatAlt from, LngLatAlt to) {
        gtfsStaticData = new ArrayList<>(gtfsStaticData);
        gtfsStaticData.sort(Comparator.comparingInt(GtfsStaticData::getSequence));

        // Find the closest shape points for 'from' and 'to'
        int fromIndex = getClosestPointIndex(from, gtfsStaticData, true);
        int toIndex = getClosestPointIndex(to, gtfsStaticData, false);

        if (fromIndex < 0 || toIndex < 0) {
            return Collections.emptyList(); // Return an empty list if any point is not found
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

    private static String getHexColor(double minAvgDelay, double maxAvgDelay, DelayAndShapeId delayAndShape) {
        double averageDelay = delayAndShape.getDelay();
        double ratio = (averageDelay - minAvgDelay) / (maxAvgDelay - minAvgDelay);
        Color green = Color.GREEN;
        Color red = Color.RED;
        int r = (int) (red.getRed() * ratio + green.getRed() * (1 - ratio));
        int g = (int) (red.getGreen() * ratio + green.getGreen() * (1 - ratio));
        int b = (int) (red.getBlue() * ratio + green.getBlue() * (1 - ratio));
        return "#" + Integer.toHexString(new Color(r, g, b).getRGB()).substring(2);
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
                            var shapesByShapeId = map.get(GtfsStaticData.TYPE.SHAPE).stream().collect(groupingBy(GtfsStaticData::getShapeIdFromId));
                            var tripsByTripId = map.get(GtfsStaticData.TYPE.TRIP).stream().collect(toMap(GtfsStaticData::getTripId, Function.identity()));
                            routeTimestamps.sort(comparing(AgencyRouteTimestamp::getTimestamp));
                            var busStates = routeTimestamps.stream().flatMap(l -> l.getBusStatesCopyList().stream()).toList();
                            Map<LngLatAlt, Map<LngLatAlt, DelayAndShapeId>> delayMapping = new HashMap<>();

                            for (String tripId : stopTimesByTripId.keySet()) {
                                String shapeId = tripsByTripId.get(tripId).getShapeId();
                                List<BusState> busStatesForTripId = busStates.stream().filter(busState -> busState.getTripId().equals(tripId)).toList();
                                List<GtfsStaticData> stopTimesForTripId = stopTimesByTripId.get(tripId);
                                stopTimesForTripId.sort(comparing(GtfsStaticData::getSequence, naturalOrder()));
                                Map<String, Integer> stopIdToSequence = stopTimesForTripId.stream().collect(toMap(GtfsStaticData::getStopId, GtfsStaticData::getSequence));
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
                                        var from = stopPositions.get(i);
                                        var to = stopPositions.get(i + 1);
                                        delayMapping.computeIfAbsent(from, k -> new HashMap<>());
                                        var currDelayAndShape = delayMapping.get(from).computeIfAbsent(to, k -> DelayAndShapeId.builder().shapeId(shapeId).delay(busStateDelay).build());
                                        currDelayAndShape.setDelay((currDelayAndShape.getDelay() + busStateDelay) / 2.);
                                    }
                                }
                            }
                            List<Feature> featureList = new ArrayList<>();
                            double maxAvgDelay = getMaxAvgDelay(delayMapping);
                            double minAvgDelay = getMinAvgDelay(delayMapping);
                            delayMapping.forEach((from, toDelay) -> {
                                toDelay.forEach((to, delayAndShape) -> {
                                    Feature feature = new Feature();
                                    LineString lineString = new LineString();
                                    lineString.setCoordinates(divideShape(shapesByShapeId.get(delayAndShape.getShapeId()), from, to));
                                    feature.setGeometry(lineString);
                                    feature.setProperty("averageDelay", delayAndShape.getDelay() / 60.);
                                    feature.setProperty("stroke", getHexColor(minAvgDelay, maxAvgDelay, delayAndShape));
                                    featureList.add(feature);
                                });
                            });
                            FeatureCollection featureCollection = new FeatureCollection();
                            featureCollection.setFeatures(featureList);
                            return featureCollection;
                        }));
    }

    private double getMinAvgDelay(Map<LngLatAlt, Map<LngLatAlt, DelayAndShapeId>> delayMapping) {
        return delayMapping.values().stream().flatMap(map -> map.values().stream()).mapToDouble(DelayAndShapeId::getDelay).min().orElse(-1);
    }

    private double getMaxAvgDelay(Map<LngLatAlt, Map<LngLatAlt, DelayAndShapeId>> delayMapping) {
        return delayMapping.values().stream().flatMap(map -> map.values().stream()).mapToDouble(DelayAndShapeId::getDelay).max().orElse(-1);
    }


    private Mono<Map<GtfsStaticData.TYPE, List<GtfsStaticData>>> groupStaticData(String feedId, List<AgencyRouteTimestamp> routeTimestamps) {
        var busStates = routeTimestamps.stream().flatMap(s -> s.getBusStatesCopyList().stream()).toList();
        var tripIds = busStates.stream().map(BusState::getTripId).distinct().toList();
        return staticRepo.findStopsTimesAndShapes(feedId, tripIds).collect(Collectors.groupingBy(GtfsStaticData::getType));
    }
}
