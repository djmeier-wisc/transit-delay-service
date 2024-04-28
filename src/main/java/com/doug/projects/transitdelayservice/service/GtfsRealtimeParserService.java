package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.UrlRedirectUtil.handleRedirect;
import static com.doug.projects.transitdelayservice.util.UrlRedirectUtil.isRedirect;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimeParserService {
    public static final String UNKNOWN_ROUTE = "UNKNOWN_ROUTE";
    public static final List<GtfsRealtime.TripDescriptor.ScheduleRelationship> ignorableScheduleRelationshipEnums =
            List.of(CANCELED);
    private final GtfsStaticRepository repository;
    @Qualifier("realtime")
    private final Executor realtimeExecutor;

    /**
     * Validate the required fields for Entity mapping
     *
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(GtfsRealtime.TripUpdate entity) {
        return !ignorableScheduleRelationshipEnums.contains(entity.getTrip().getScheduleRelationship());
    }

    @NotNull
    private AgencyRouteTimestamp getAgencyRouteTimestamp(String agencyId, Map.Entry<String, List<GtfsRealtime.TripUpdate>> entry, long timeStamp) {
        String routeName = entry.getKey();
        List<GtfsRealtime.TripUpdate> tripUpdates = entry.getValue();
        AgencyRouteTimestamp agencyRouteTimestamp = new AgencyRouteTimestamp();
        agencyRouteTimestamp.setAgencyRoute(agencyId, routeName.replace(":", ""));
        agencyRouteTimestamp.setTimestamp(timeStamp);
        agencyRouteTimestamp.setBusStates(adaptBusStatesFrom(agencyId, tripUpdates));
        return agencyRouteTimestamp;
    }

    private List<BusState> adaptBusStatesFrom(String feedId, List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream().map(tripUpdate -> this.adaptBusStateFrom(feedId, tripUpdate)).toList();
    }

    @NotNull
    private BusState adaptBusStateFrom(String feedId, GtfsRealtime.TripUpdate tripUpdate) {
        BusState busState = new BusState();
        busState.setTripId(tripUpdate.getTrip().getTripId());
        if (tripUpdate.getStopTimeUpdateCount() <= 0) {
            busState.setDelay(tripUpdate.getDelay());
            return busState;
        }
        busState.setDelay(getDelay(feedId, tripUpdate));
        busState.setClosestStopId(tripUpdate.getStopTimeUpdate(0).getStopId());
        return busState;
    }

    /**
     * This method extracts delay from a tripUpdate. Note that
     *
     * @param feedId
     * @param tu
     * @return
     */
    private int getDelay(String feedId, GtfsRealtime.TripUpdate tu) {
        var currStopTimeUpdate = tu.getStopTimeUpdate(0);
        var arrival = currStopTimeUpdate.getArrival();
        var departure = currStopTimeUpdate.getDeparture();
        if (arrival.hasDelay()) {
            return arrival.getDelay();
        } else if (departure.hasDelay()) {
            return departure.getDelay();
        }
        return extractDiffTimeFromDb(feedId, tu, currStopTimeUpdate, departure, arrival);
    }

    private int extractDiffTimeFromDb(String feedId, GtfsRealtime.TripUpdate tu, GtfsRealtime.TripUpdate.StopTimeUpdate currStopTimeUpdate, GtfsRealtime.TripUpdate.StopTimeEvent departure, GtfsRealtime.TripUpdate.StopTimeEvent arrival) {
        if (currStopTimeUpdate.hasDeparture() && departure.hasTime()) {
            var actualDeparture = departure.getTime();
            var expectedDeparture = repository
                    .getStopTimeById(feedId, tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence())
                    .mapNotNull(GtfsStaticData::getDepartureTimestamp)
                    .blockOptional();
            if (expectedDeparture.isEmpty()) return 0;
            return (int) ((actualDeparture - expectedDeparture.get()) / 1000);
        } else if (currStopTimeUpdate.hasArrival() && arrival.hasTime()) {
            var actualArrival = arrival.getTime();
            var expectedArrival = repository
                    .getStopTimeById(feedId, tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence())
                    .mapNotNull(GtfsStaticData::getArrivalTimestamp)
                    .blockOptional();
            if (expectedArrival.isEmpty()) return 0;
            return (int) ((actualArrival - expectedArrival.get()) / 1000);
        } else {
            return 0;
        }
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

    private AgencyRealtimeResponse doRPCRequest(AgencyFeed feed, String feedId, String realtimeUrl) throws IOException {
        var fileStream = java.net.URI.create(realtimeUrl).toURL().openStream();
        GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(fileStream);
        long timeStamp = feedMessage.getHeader().getTimestamp();

        List<GtfsRealtime.TripUpdate> tripUpdates = feedMessage.getEntityList().stream()
                .map(GtfsRealtime.FeedEntity::getTripUpdate).toList();

        Map<String, String> tripIdToRouteNameMap = repository.mapTripIdsToRouteName(feedId, getTripIds(tripUpdates));
        Map<String, String> routeIdToRouteNameMap = repository.mapRouteIdsToRouteName(feedId, getRouteIds(tripUpdates));

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
                .map(entry -> getAgencyRouteTimestamp(feedId, entry, timeStamp))
                .toList();
        log.info("Read {} realtime feed entries from id: {}, url: {}", routeTimestampList.size(), feedId, realtimeUrl);
        fileStream.close();
        return AgencyRealtimeResponse.builder()
                .feed(feed)
                .feedStatus(AgencyFeed.Status.ACTIVE)
                .routeTimestamps(routeTimestampList)
                .build();
    }
}
