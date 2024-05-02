package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.UrlRedirectUtil.handleRedirect;
import static com.doug.projects.transitdelayservice.util.UrlRedirectUtil.isRedirect;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimeParserService {
    public static final String UNKNOWN_ROUTE = "UNKNOWN_ROUTE";
    public static final List<GtfsRealtime.TripDescriptor.ScheduleRelationship> ignorableScheduleRelationshipEnums =
            List.of(CANCELED);
    private final GtfsStaticRepository staticRepository;
    @Qualifier("realtime")
    private final Executor realtimeExecutor;
    private final ExpectedBusTimesService expectedBusTimesService;

    /**
     * Validate the required fields for Entity mapping
     *
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(GtfsRealtime.TripUpdate entity) {
        return entity.getTrip().getScheduleRelationship().equals(SCHEDULED);
    }

    @NotNull
    private static BusState adaptBusStateFrom(GtfsRealtime.TripUpdate tripUpdate, ExpectedBusTimes tripMap) {
        BusState busState = new BusState();
        busState.setTripId(tripUpdate.getTrip().getTripId());
        if (tripUpdate.getStopTimeUpdateCount() <= 0) {
            busState.setDelay(tripUpdate.getDelay());
            return busState;
        }
        busState.setDelay(getDelay(tripUpdate, tripMap));
        busState.setClosestStopId(tripUpdate.getStopTimeUpdate(0).getStopId());
        return busState;
    }

    /**
     * This method extracts delay from a tripUpdate. Note that it will first try to get departureDelay,
     * then arrivalDelay, then departureTime (diffed with schedule), then arrivalTime (diffed with schedule).
     * Will return null in the case of not finding anything in the schedule, or if no details are passed.
     *
     * @param tu the tripUpdate
     * @param tripMap
     * @return
     */
    private static Integer getDelay(GtfsRealtime.TripUpdate tu, ExpectedBusTimes tripMap) {
        if (tu.hasDelay()) {
            return tu.getDelay();
        }
        var departureDelay = getDepartureDelay(tu);
        if (departureDelay.isPresent()) {
            return departureDelay.get();
        }
        var arrivalDelay = getArrivalDelay(tu);
        if (arrivalDelay.isPresent()) {
            return arrivalDelay.get();
        }
        if (tripMap == null) {
            log.error("TripMap was unexpectedly null!");
            return null;
        }
        return extractDifferenceFromActualAndExpectedTime(tu, tripMap);
    }

    private static @NotNull Optional<Integer> getDepartureDelay(GtfsRealtime.TripUpdate tu) {
        return tu.getStopTimeUpdateList()
                .stream()
                .map(GtfsRealtime.TripUpdate.StopTimeUpdate::getDeparture)
                .filter(GtfsRealtime.TripUpdate.StopTimeEvent::hasDelay)
                .map(GtfsRealtime.TripUpdate.StopTimeEvent::getDelay)
                .findFirst();
    }

    private static @NotNull Optional<Integer> getArrivalDelay(GtfsRealtime.TripUpdate tu) {
        return tu.getStopTimeUpdateList()
                .stream()
                .map(GtfsRealtime.TripUpdate.StopTimeUpdate::getArrival)
                .filter(GtfsRealtime.TripUpdate.StopTimeEvent::hasDelay)
                .map(GtfsRealtime.TripUpdate.StopTimeEvent::getDelay)
                .findFirst();
    }

    private static Integer extractDifferenceFromActualAndExpectedTime(GtfsRealtime.TripUpdate tu, ExpectedBusTimes tripMap) {
        var currStopTimeUpdate = tu.getStopTimeUpdate(0);
        var arrival = currStopTimeUpdate.getArrival();
        var departure = currStopTimeUpdate.getDeparture();
        var timezone = tripMap.getTimezone();
        if (currStopTimeUpdate.hasDeparture() && departure.hasTime()) {
            var actualDeparture = departure.getTime();
            var expectedDeparture = tripMap.getDepartureTime(tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());
            if (isEmpty(expectedDeparture) || isEmpty(timezone) || expectedDeparture.isEmpty()) {
                return null;
            }
            return (int) TransitDateUtil.calculateTimeDifferenceInSeconds(expectedDeparture.get(), actualDeparture, timezone);
        } else if (currStopTimeUpdate.hasArrival() && arrival.hasTime()) {
            var actualArrival = arrival.getTime();
            var expectedArrival = tripMap.getDepartureTime(tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());
            if (isEmpty(expectedArrival) || isEmpty(timezone) || expectedArrival.isEmpty()) {
                return null;
            }
            return (int) TransitDateUtil.calculateTimeDifferenceInSeconds(expectedArrival.get(), actualArrival, timezone);
        } else {
            return null;
        }
    }

    private static Map<String, Integer> getTripsWithoutDelayAttribute(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .filter(GtfsRealtimeParserService::doesTripNotHaveDelayAttribute)
                .filter(GtfsRealtimeParserService::hasTripIdAndStopSequence)
                .collect(Collectors.toMap(GtfsRealtimeParserService::getTripId, GtfsRealtimeParserService::getStopSequence, (a, b) -> a));
    }

    private static boolean hasTripIdAndStopSequence(GtfsRealtime.TripUpdate tripUpdate) {
        return tripUpdate.getTrip().hasTripId() && tripUpdate.getStopTimeUpdateCount() > 0;
    }

    @NotNull
    private static List<String> getTripIds(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .map(GtfsRealtime.TripUpdate::getTrip)
                .map(GtfsRealtime.TripDescriptor::getTripId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    @NotNull
    private static List<String> getRouteIds(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .map(GtfsRealtime.TripUpdate::getTrip)
                .map(GtfsRealtime.TripDescriptor::getRouteId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private String getRouteName(GtfsRealtime.TripUpdate tripUpdate, Map<String, String> tripMap, Map<String, String> routeMap) {
        String routeId = tripUpdate.getTrip().getRouteId();
        if (StringUtils.isNotBlank(routeId) && routeMap.containsKey(routeId)) {
            return routeMap.get(routeId);

        }
        String tripId = tripUpdate.getTrip().getTripId();
        if (StringUtils.isNotBlank(tripId) && tripMap.containsKey(tripId)) {
            return tripMap.get(tripId);
        }
        return UNKNOWN_ROUTE;
    }

    public CompletableFuture<AgencyRealtimeResponse> convertFromAsync(AgencyFeed feed, int timeoutSeconds) {
        var timeoutFailure = AgencyRealtimeResponse.builder().feedStatus(AgencyFeed.Status.TIMEOUT).feed(feed).build();
        return CompletableFuture
                .supplyAsync(() -> convertFromSync(feed), realtimeExecutor)
                .completeOnTimeout(timeoutFailure, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Parses the realTime feed from AgencyFeed.
     *
     * @param feed the feed to try to read from the realtime url
     * @return an AgencyRealTimeResponse.
     * <p>In the event of failure, status will be set and routeTimestamps field may be null or empty</p>
     */
    private AgencyRealtimeResponse convertFromSync(AgencyFeed feed) {
        String feedId = feed.getId();
        String realtimeUrl = feed.getRealTimeUrl();

        try {
            log.info("Reading realtime feed from id: {}, url: {}", feedId, realtimeUrl);
            var conn = ((HttpURLConnection) new URL(realtimeUrl).openConnection());
            if (isRedirect(conn)) {
                feed.setRealTimeUrl(handleRedirect(conn));
                return convertFromSync(feed);
            } else if (StringUtils.contains("401", conn.getResponseMessage()) ||
                    conn.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    conn.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                log.error("Connection is unauthorized for feedId: {}", feedId);
                return AgencyRealtimeResponse.builder()
                        .feedStatus(AgencyFeed.Status.UNAUTHORIZED)
                        .feed(feed)
                        .build();
            } else if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.error("Failed to download realtime data from \"{}\", resCode \"\"", feedId);
                return AgencyRealtimeResponse.builder()
                        .feed(feed)
                        .feedStatus(AgencyFeed.Status.UNAVAILABLE)
                        .build();
            }
            return doRPCRequest(feed, feedId, realtimeUrl);
        } catch (IOException e) {
            log.error("Error reading realtime feed from id: {}, url: {}, message: {}", feedId, realtimeUrl, e.getMessage());
            return AgencyRealtimeResponse.builder()
                    .feed(feed)
                    .feedStatus(AgencyFeed.Status.TIMEOUT)
                    .build();
        }
    }

    private static boolean doesTripNotHaveDelayAttribute(GtfsRealtime.TripUpdate tripUpdate) {
        return !(tripUpdate.hasDelay() ||
                tripUpdate.getStopTimeUpdateList().stream().anyMatch(s -> s.getDeparture().hasDelay()) ||
                tripUpdate.getStopTimeUpdateList().stream().anyMatch(s -> s.getArrival().hasDelay()));
    }

    private static String getTripId(GtfsRealtime.TripUpdate tripUpdate) {
        return tripUpdate.getTrip().getTripId();
    }

    private static int getStopSequence(GtfsRealtime.TripUpdate tripUpdate) {
        return tripUpdate.getStopTimeUpdate(0).getStopSequence();
    }

    @NotNull
    private AgencyRouteTimestamp getAgencyRouteTimestamp(String agencyId,
                                                         Map.Entry<String, List<GtfsRealtime.TripUpdate>> routeNameToTripUpdate,
                                                         long timeStamp, ExpectedBusTimes tripMap) {
        String routeName = routeNameToTripUpdate.getKey();
        List<GtfsRealtime.TripUpdate> tripUpdates = routeNameToTripUpdate.getValue();
        AgencyRouteTimestamp agencyRouteTimestamp = new AgencyRouteTimestamp();
        agencyRouteTimestamp.setAgencyRoute(agencyId, routeName.replace(":", ""));
        agencyRouteTimestamp.setTimestamp(timeStamp);
        agencyRouteTimestamp.setBusStates(adaptBusStatesFrom(tripUpdates, tripMap));
        return agencyRouteTimestamp;
    }

    private List<BusState> adaptBusStatesFrom(List<GtfsRealtime.TripUpdate> tripUpdates, ExpectedBusTimes tripMap) {
        return tripUpdates.stream().map(tripUpdate ->
                        adaptBusStateFrom(tripUpdate, tripMap))
                .toList();
    }

    private AgencyRealtimeResponse doRPCRequest(AgencyFeed feed, String feedId, String realtimeUrl) throws IOException {
        var fileStream = java.net.URI.create(realtimeUrl).toURL().openStream();
        GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(fileStream);
        long timeStamp = feedMessage.getHeader().getTimestamp();

        List<GtfsRealtime.TripUpdate> tripUpdates = feedMessage.getEntityList().stream()
                .map(GtfsRealtime.FeedEntity::getTripUpdate).toList();

        Map<String, String> tripIdToRouteNameMap = staticRepository.mapTripIdsToRouteName(feedId, getTripIds(tripUpdates));
        Map<String, String> routeIdToRouteNameMap = staticRepository.mapRouteIdsToRouteName(feedId, getRouteIds(tripUpdates));
        //key=tripId, value=stopSequence
        Map<String, Integer> tripsWithoutDelayAttribute = getTripsWithoutDelayAttribute(tripUpdates);
        var tripMap = expectedBusTimesService.getTripMapFor(feedId, tripsWithoutDelayAttribute).block();
        var routeNameToTripUpdateMap = tripUpdates.stream()
                .filter(GtfsRealtimeParserService::validateRequiredFields)
                .collect(Collectors.groupingBy(fe -> getRouteName(fe, tripIdToRouteNameMap, routeIdToRouteNameMap)));

        if (routeNameToTripUpdateMap.containsKey(UNKNOWN_ROUTE)) {
            log.error("FeedId: {} is outdated! Sending for reevaluation", feed.getId());
            return AgencyRealtimeResponse.builder()
                    .feed(feed)
                    .feedStatus(AgencyFeed.Status.OUTDATED)
                    .build();
        }
        List<AgencyRouteTimestamp> routeTimestampList = routeNameToTripUpdateMap
                .entrySet()
                .stream()
                .map(entry -> getAgencyRouteTimestamp(feedId, entry, timeStamp, tripMap))
                .toList();
        log.info("Read {} realtime feed entries from id: {}, url: {}", routeTimestampList.size(), feedId, realtimeUrl);
        fileStream.close();
        if (containsNullDelay(routeTimestampList)) {
            log.error("Feed {} had null delay!.", feedId);
            return AgencyRealtimeResponse.builder()
                    .feed(feed)
                    .feedStatus(AgencyFeed.Status.OUTDATED)
                    .build();
        }
        return AgencyRealtimeResponse.builder()
                .feed(feed)
                .feedStatus(AgencyFeed.Status.ACTIVE)
                .routeTimestamps(routeTimestampList)
                .build();
    }

    private boolean containsNullDelay(List<AgencyRouteTimestamp> routeTimestampList) {
        return routeTimestampList.stream()
                .flatMap(rt -> rt.getBusStatesCopyList().stream())
                .map(BusState::getDelay)
                .anyMatch(Objects::isNull);
    }
}
