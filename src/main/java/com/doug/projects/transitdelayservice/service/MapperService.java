package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.GtfsShape;
import com.doug.projects.transitdelayservice.entity.MapOptions;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.jpa.*;
import com.doug.projects.transitdelayservice.entity.transit.ShapeProperties;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyShapeRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticService;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyStopTimeRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geojson.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.*;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapperService {
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final GtfsStaticService staticRepo;
    private final AgencyFeedService agencyFeedService;
    private final AgencyShapeRepository agencyShapeRepository;
    private final AgencyStopTimeRepository agencyStopTimeRepository;

    public static List<LngLatAlt> divideShape(List<LngLatAlt> lngLatAlts, LngLatAlt from, LngLatAlt to) {

        // Find the closest shape points for 'from' and 'to'
        int fromIndex = getClosestPointIndex(from, lngLatAlts, true);
        int toIndex = getClosestPointIndex(to, lngLatAlts, false);

        if (fromIndex < 0 || toIndex < 0) {
            return emptyList(); // Return an empty list if any point is not found
        }

        if (fromIndex > toIndex) {
            // If fromShape comes after toIndex in the sequence, swap them
            int temp = fromIndex;
            fromIndex = toIndex;
            toIndex = temp;
        }

        return lngLatAlts.subList(fromIndex, toIndex + 1);
    }

    /**
     * Gets the closest point index.
     *
     * @param point           the point we search shapes against
     * @param shapes          a list of gtfs shape data, used to compare distance
     * @param findLowestIndex if multiple distances are equal, whether to return the lowest or highest one
     * @return the index of the shape closes to point
     */
    private static int getClosestPointIndex(LngLatAlt point, List<LngLatAlt> shapes, boolean findLowestIndex) {
        double minDistance = Double.MAX_VALUE;
        int closestPoint = -1;
        if (findLowestIndex) { //record the lowest index, provided there are two equal ones
            for (int i = 0; i < shapes.size(); i++) {
                LngLatAlt shape = shapes.get(i);
                double distance = haversineDistance(point.getLatitude(), point.getLongitude(), shape.getLatitude(), shape.getLongitude());
                if (distance < minDistance) {
                    minDistance = distance;
                    closestPoint = i;
                }
            }
        } else {
            for (int i = shapes.size() - 1; i >= 0; i--) {
                LngLatAlt shape = shapes.get(i);
                double distance = haversineDistance(point.getLatitude(), point.getLongitude(), shape.getLatitude(), shape.getLongitude());
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
     * @return
     */
    @NotNull
    protected Map<LngLatAlt, Map<LngLatAlt, ShapeProperties>> getStopDelayMapping(List<AgencyRouteTimestamp> routeTimestamps) {
        var busStatesByTripId = routeTimestamps.stream()
                .sorted(comparing(AgencyRouteTimestamp::getTimestamp))
                .flatMap(l -> l.getBusStatesCopyList()
                        .stream())
                .collect(groupingBy(BusState::getTripId));
        Map<AgencyTrip, List<AgencyStopTime>> stopTimesByTripId =
                agencyStopTimeRepository.findAllByTrip_IdIn(busStatesByTripId.keySet())
                        .stream()
                        .collect(Collectors.groupingBy(AgencyStopTime::getTrip));
        Map<LngLatAlt, Map<LngLatAlt, ShapeProperties>> delayMapping = new HashMap<>();
        for (AgencyTrip tripId : stopTimesByTripId.keySet()) {
            List<BusState> busStatesForTripId = busStatesByTripId.getOrDefault(tripId.getId(), emptyList());
            List<AgencyStopTime> stopTimesForTripId = stopTimesByTripId.getOrDefault(tripId, emptyList());
            stopTimesForTripId.sort(comparing(AgencyStopTime::getStopSeq, naturalOrder()));
            Map<String, Integer> stopIdToSequence = stopTimesForTripId.stream()
                    .collect(toMap(s->s.getStop().getId(), AgencyStopTime::getStopSeq, (a, b) -> a));
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
                LngLatAlt[] stopPositions = getStopPositions(fromStopSeq, toStopSeq, stopTimesForTripId);
                double[] interpolatedDelays = interpolate(fromDelay, toDelay, toStopSeq - fromStopSeq);
                for (int i = 0; i < stopPositions.length - 1; i++) {
                    var fromStop = stopPositions[i];
                    var toStop = stopPositions[i + 1];
                    double currDelay = interpolatedDelays[i];
                    var currDelayAndShape = delayMapping
                            .computeIfAbsent(fromStop, k -> new HashMap<>()) //build new HashMap if none are present for "from"
                            .computeIfAbsent(toStop, k -> ShapeProperties.builder()
                                    .shapeId(tripId.getShapePoints().stream().map(s->new LngLatAlt(s.getShapePtLon(),s.getShapePtLat())).toList())
                                    .delay(currDelay)
                                    .count(0)
                                    .build());
                    currDelayAndShape.setDelay((currDelayAndShape.getDelay() + fromDelay));
                    currDelayAndShape.setCount(currDelayAndShape.getCount() + 1);
                }
            }
        }
        for (Map<LngLatAlt, ShapeProperties> map : delayMapping.values()) {
            for (ShapeProperties props : map.values()) {
                props.setDelay(props.getDelay() / props.getCount());
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
                                                         List<AgencyStopTime> stopTimesForTripId) {
        LngLatAlt[] stopPositions = new LngLatAlt[secondStopSequence - firstStopSequence];
        for (int stopTimeIndex = firstStopSequence; stopTimeIndex < secondStopSequence; stopTimeIndex++) {
            AgencyStopTime stopTime = stopTimesForTripId.get(stopTimeIndex);
            AgencyStop stop = stopTime.getStop();
            int pos = stopTimeIndex - firstStopSequence;
            stopPositions[pos] = new LngLatAlt(stop.getStopLon(), stop.getStopLat());
        }
        return stopPositions;
    }

    public FeatureCollection getDelayLines(String feedId, MapOptions mapOptions) {
        if (CollectionUtils.isEmpty(mapOptions.getRouteNames()) || StringUtils.isBlank(feedId)) {
            log.error("Failed either due to empty feedId or empty routeName");
            return new FeatureCollection();
        }
        //this looks weird, but it was the easiest way to zip together a single timeZone with many
        String agencyTimezone = agencyFeedService.getAgencyFeedById(feedId)
                .map(AgencyFeedDto::getTimezone)
                .orElse("");
        var routeTimestamps = routeTimestampRepository.getRouteTimestampsBy(getMidnightDaysAgo(mapOptions.getSearchPeriod()),
                getMidnightTonight(),
                mapOptions.getRouteNames(),
                feedId).collectList().block();

        var filteredRouteTimestamps = routeTimestamps
                .stream()
                .filter(routeTimestamp -> {
                    if (StringUtils.isEmpty(agencyTimezone)) {
                        return true;
                    }
                    var date = Instant.ofEpochSecond(routeTimestamp.getTimestamp());

                    var sampledHour = date.atZone(ZoneId.of(agencyTimezone)).getHour();
                    var sampledDay = date.atZone(ZoneId.of(agencyTimezone)).getDayOfWeek();
                    return sampledHour >= mapOptions.getHourStarted() &&
                            sampledHour <= mapOptions.getHourEnded() &&
                            mapOptions.getDaysSelected().contains(sampledDay.getValue());
                }).toList();
        var stopDelayMapping = getStopDelayMapping(filteredRouteTimestamps);
        var featureList = getFeatureList(stopDelayMapping);
        return getFeatureCollection(featureList);
    }

    private @NotNull FeatureCollection getFeatureCollection(List<Feature> map) {
        FeatureCollection featureCollection = new FeatureCollection();
        featureCollection.setFeatures(map);
        return featureCollection;
    }

    private @NotNull List<Feature> getFeatureList(Map<LngLatAlt,
            Map<LngLatAlt, ShapeProperties>> delayMapping) {
        List<Feature> featureList = new ArrayList<>();
        delayMapping.forEach((from, toDelay) -> {
            toDelay.forEach((to, delayAndShape) -> {
                Feature feature = new Feature();
                LineString lineString = new LineString();
                lineString.setCoordinates(divideShape(delayAndShape.getShapeId(), from, to));
                feature.setGeometry(lineString);
                feature.setProperty("averageDelay", delayAndShape.getDelay() / 60.);
                featureList.add(feature);
            });
        });
        return featureList;
    }

    public GtfsShape getRandomGtfsShape(String feedId) {
        long count = agencyShapeRepository.countByAgencyTripRouteAgency_Id(feedId);
        if (count == 0) return new GtfsShape();
        int selected = (int) (Math.random() * count);
        var points = agencyShapeRepository.findAllByAgencyTripRouteAgency_Id(feedId, PageRequest.of(selected,1))
                .stream()
                .findAny()
                .map(AgencyShape::getId)
                .map(ShapePointId::getShapeId)
                .map(agencyShapeRepository::findAllById_ShapeIdOrderByIdShapeIdAsc)
                .stream()
                .flatMap(Collection::stream)
                .map(s->List.of(s.getShapePtLat(),s.getShapePtLon()))
                .toList();
        return GtfsShape.builder()
                .shape(points)
                .build();
    }
}
