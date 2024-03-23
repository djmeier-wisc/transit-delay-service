package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimeParserService {
    public static final String UNKNOWN_ROUTE = "UNKNOWN_ROUTE";
    private final GtfsStaticRepository repository;

    /**
     * Validate the required fields for Entity mapping
     *
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(GtfsRealtime.TripUpdate entity) {
        return true;
    }

    @NotNull
    private static AgencyRouteTimestamp getAgencyRouteTimestamp(String agencyId, Map.Entry<String, List<GtfsRealtime.TripUpdate>> entry, long timeStamp) {
        String routeName = entry.getKey();
        List<GtfsRealtime.TripUpdate> tripUpdates = entry.getValue();
        AgencyRouteTimestamp agencyRouteTimestamp = new AgencyRouteTimestamp();
        agencyRouteTimestamp.setAgencyRoute(agencyId, routeName.replace(":", ""));
        agencyRouteTimestamp.setTimestamp(timeStamp);
        agencyRouteTimestamp.setBusStates(adaptBusStatesFrom(tripUpdates));
        return agencyRouteTimestamp;
    }

    private static List<BusState> adaptBusStatesFrom(List<GtfsRealtime.TripUpdate> tripUpdate) {
        return tripUpdate.stream().map(GtfsRealtimeParserService::adaptBusStateFrom).toList();
    }

    @NotNull
    private static BusState adaptBusStateFrom(GtfsRealtime.TripUpdate tu) {
        BusState busState = new BusState();
        busState.setDelay(tu.getDelay());
        if (tu.getStopTimeUpdateCount() > 0)
            busState.setClosestStopId(tu.getStopTimeUpdate(0).getStopId());
        busState.setTripId(tu.getTrip().getTripId());
        return busState;
    }

    private static boolean isRedirect(HttpURLConnection conn) throws IOException {
        return conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP;
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
                .supplyAsync(() -> convertFromSync(feed))
                .completeOnTimeout(timeoutFailure, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Parses the realTime feed from AgencyFeed.
     *
     * @param feed        the feed to try to read from the realtime url
     * @param realtimeUrl
     * @return an AgencyRealTimeResponse.
     * <p>In the event of failure, status will be set and routeTimestamps field may be null or empty</p>
     */
    public AgencyRealtimeResponse convertFromSync(AgencyFeed feed) {
        String feedId = feed.getId();
        String realtimeUrl = feed.getRealTimeUrl();

        try {
            log.info("Reading realtime feed from id: {}, url: {}", feedId, realtimeUrl);
            var conn = ((HttpURLConnection) new URL(realtimeUrl).openConnection());
            if (isRedirect(conn)) {
                log.info("Redirected id \"{}\" to \"{}\"", feedId, realtimeUrl);
                //call new, redirected url
                //this should be in this "Location" header
                feed.setRealTimeUrl(conn.getHeaderField("Location"));
                return convertFromSync(feed);
            } else if (StringUtils.contains("401", conn.getResponseMessage()) || conn.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                log.error("Connection is unauthorized for feedId: {}", feedId);
                return AgencyRealtimeResponse.builder().feedStatus(AgencyFeed.Status.UNAUTHORIZED).build();
            } else if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.error("Failed to download realtime data from \"{}\", resCode \"\"", realtimeUrl);
                return AgencyRealtimeResponse.builder()
                        .feed(feed)
                        .feedStatus(AgencyFeed.Status.UNAVAILABLE)
                        .build();
            }
            return doRPCRequest(feed, feedId, realtimeUrl);
        } catch (IOException e) {
            log.error("Error reading realtime feed from id: {}, url: {}, message: {}", feedId, realtimeUrl, e.getMessage());
            return AgencyRealtimeResponse.builder().feed(feed).feedStatus(AgencyFeed.Status.UNAVAILABLE).build();
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
