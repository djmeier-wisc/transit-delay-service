package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.BusState;
import com.doug.projects.transitdelayservice.entity.GtfsShape;
import com.doug.projects.transitdelayservice.entity.MapOptions;
import com.doug.projects.transitdelayservice.entity.jpa.*;
import com.doug.projects.transitdelayservice.entity.transit.ShapeProperties;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyShapeRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyStopTimeRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LineString;
import org.geojson.LngLatAlt;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightDaysAgo;
import static com.doug.projects.transitdelayservice.util.TransitDateUtil.getMidnightTonight;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapperService {
    private final AgencyRouteTimestampRepository routeTimestampRepository;
    private final AgencyFeedService agencyFeedService;
    private final AgencyStopTimeRepository agencyStopTimeRepository;
    private final AgencyShapeRepository agencyShapeRepository;

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

    public GtfsShape getRandomGtfsShape(String feedId) {
        long count = agencyShapeRepository.countById_AgencyId(feedId);
        if (count == 0) return new GtfsShape();
        int selected = (int) (Math.random() * count);
        var points = agencyShapeRepository.findAllById_AgencyId(feedId, PageRequest.of(selected, 1))
                .stream()
                .findAny()
                .map(AgencyShape::getAgencyShapePoints)
                .stream()
                .flatMap(Collection::stream)
                .map(s->List.of(s.getShapePtLat(),s.getShapePtLon()))
                .toList();
        return GtfsShape.builder()
                .shape(points)
                .build();
    }

    @Transactional(readOnly = true)
    public FeatureCollection getDelayLines(String feedId, MapOptions mapOptions) {
        if (CollectionUtils.isEmpty(mapOptions.getRouteNames()) || StringUtils.isBlank(feedId)) {
            log.error("Failed due to empty feedId or routeNames");
            return new FeatureCollection();
        }

        String agencyTimezone = agencyFeedService.getAgencyFeedById(feedId)
                .map(AgencyFeedDto::getTimezone)
                .orElse("UTC");

        var routeTimestamps = routeTimestampRepository.getRouteTimestampsBy(
                getMidnightDaysAgo(mapOptions.getSearchPeriod()),
                getMidnightTonight(),
                mapOptions.getRouteNames(),
                feedId);

        var filteredRouteTimestamps = routeTimestamps.stream()
                .filter(rt -> isWithinTimeWindow(rt, agencyTimezone, mapOptions))
                .toList();

        Map<String, ShapeProperties> segmentDelayMapping = getSegmentDelayMapping(filteredRouteTimestamps, feedId);
        return getFeatureCollection(segmentDelayMapping);
    }

    private boolean isWithinTimeWindow(AgencyRouteTimestamp rt, String agencyTimezone, MapOptions mapOptions) {
        if (StringUtils.isEmpty(agencyTimezone)) {
            return true;
        }
        var date = Instant.ofEpochSecond(rt.getTimestamp());

        var sampledHour = date.atZone(ZoneId.of(agencyTimezone)).getHour();
        var sampledDay = date.atZone(ZoneId.of(agencyTimezone)).getDayOfWeek();
        return sampledHour >= mapOptions.getHourStarted() &&
                sampledHour <= mapOptions.getHourEnded() &&
                mapOptions.getDaysSelected().contains(sampledDay.getValue());
    }

    protected Map<String, ShapeProperties> getSegmentDelayMapping(List<AgencyRouteTimestamp> routeTimestamps, String feedId) {
        // 1. Group snapshots by Trip ID and sort by time to track bus progress correctly
        Map<AgencyTripId, List<BusState>> busHistory = routeTimestamps.stream()
                .sorted(comparing(AgencyRouteTimestamp::getTimestamp))
                .flatMap(rt -> rt.getBusStatesCopyList().stream())
                .collect(groupingBy(s -> new AgencyTripId(s.getTripId(), feedId)));

        // 2. Fetch all stop times for these trips
        Map<AgencyTrip, List<AgencyStopTime>> tripStopData = agencyStopTimeRepository.findAllByTrip_IdIn(busHistory.keySet())
                .stream()
                .collect(groupingBy(AgencyStopTime::getTrip));

        Map<String, ShapeProperties> aggregationMap = new HashMap<>();

        for (Map.Entry<AgencyTrip, List<AgencyStopTime>> entry : tripStopData.entrySet()) {
            AgencyTrip trip = entry.getKey();
            List<AgencyStopTime> stops = entry.getValue();

            // Map by Sequence ID to fix the indexing bug
            Map<Integer, AgencyStopTime> seqToStop = stops.stream()
                    .collect(toMap(AgencyStopTime::getStopSeq, s -> s, (a, b) -> a));
            List<Integer> sortedSeqs = seqToStop.keySet().stream().sorted().toList();

            List<BusState> states = busHistory.getOrDefault(trip.getId(), emptyList());

            for (int i = 0; i < states.size() - 1; i++) {
                BusState startState = states.get(i);
                BusState endState = states.get(i + 1);

                Integer fromSeq = findSequenceForStop(startState.getClosestStopId(), stops);
                Integer toSeq = findSequenceForStop(endState.getClosestStopId(), stops);

                if (fromSeq == null || toSeq == null || toSeq <= fromSeq) continue;

                // Identify all segments between these two snapshots
                List<Integer> segmentsInInterval = sortedSeqs.stream()
                        .filter(s -> s >= fromSeq && s <= toSeq)
                        .toList();

                double[] interpolatedDelays = interpolate(
                        startState.getDelay() != null ? startState.getDelay() : 0,
                        endState.getDelay() != null ? endState.getDelay() : 0,
                        segmentsInInterval.size() - 1
                );

                for (int j = 0; j < segmentsInInterval.size() - 1; j++) {
                    AgencyStopTime s1 = seqToStop.get(segmentsInInterval.get(j));
                    AgencyStopTime s2 = seqToStop.get(segmentsInInterval.get(j + 1));

                    String segmentId = s1.getStopId() + "->" + s2.getStopId();
                    double segmentDelay = interpolatedDelays[j];

                    ShapeProperties props = aggregationMap.computeIfAbsent(segmentId, k -> ShapeProperties.builder()
                            .shapeId(extractShapePoints(trip))
                            .fromStop(new LngLatAlt(s1.getStop().getStopLon(), s1.getStop().getStopLat()))
                            .toStop(new LngLatAlt(s2.getStop().getStopLon(), s2.getStop().getStopLat()))
                            .delay(0.0)
                            .count(0)
                            .build());

                    props.setDelay(props.getDelay() + segmentDelay);
                    props.setCount(props.getCount() + 1);
                }
            }
        }

        // Final Average
        aggregationMap.values().forEach(p -> p.setDelay(p.getDelay() / p.getCount()));
        return aggregationMap;
    }

    private List<LngLatAlt> extractShapePoints(AgencyTrip trip) {
        return trip.getAgencyShapePoints().stream()
                .map(p -> new LngLatAlt(p.getShapePtLon(), p.getShapePtLat()))
                .toList();
    }

    private Integer findSequenceForStop(String stopId, List<AgencyStopTime> stops) {
        return stops.stream()
                .filter(s -> s.getStopId().equals(stopId))
                .map(AgencyStopTime::getStopSeq)
                .findFirst()
                .orElse(null);
    }

    private FeatureCollection getFeatureCollection(Map<String, ShapeProperties> segmentMapping) {
        FeatureCollection collection = new FeatureCollection();
        List<Feature> features = new ArrayList<>();

        segmentMapping.forEach((id, props) -> {
            Feature f = new Feature();
            LineString ls = new LineString();
            ls.setCoordinates(divideShape(props.getShapeId(), props.getFromStop(), props.getToStop()));
            f.setGeometry(ls);
            f.setProperty("averageDelay", props.getDelay() / 60.0);
            features.add(f);
        });

        collection.setFeatures(features);
        return collection;
    }
}
