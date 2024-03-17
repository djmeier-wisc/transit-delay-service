package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimeParserService {
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

    private String getRouteName(String agencyId, GtfsRealtime.TripUpdate tripUpdate) {
        String routeId = tripUpdate.getTrip().getRouteId();
        String tripId = tripUpdate.getTrip().getTripId();
        Optional<String> routeName = repository.getRouteNameByRoute(agencyId, routeId);
        if (routeName.isEmpty()) {
            routeName = repository.getRouteNameByTrip(agencyId, tripId);
        }
        return routeName.orElse("UNKNOWN_ROUTE");
    }

    @NotNull
    private static BusState adaptBusStateFrom(GtfsRealtime.TripUpdate tu) {
        BusState busState = new BusState();
        busState.setDelay(tu.getDelay());
        busState.setClosestStopId(tu.getStopTimeUpdate(0).getStopId());
        busState.setTripId(tu.getTrip().getTripId());
        return busState;
    }

    public CompletableFuture<List<AgencyRouteTimestamp>> convertFromAsync(String agencyId, String realtimeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convertFromSync(agencyId, realtimeUrl);
            } catch (IOException e) {
                log.error("IOException in RT feed: {}", realtimeUrl, e);
                return Collections.emptyList();
            }
        });
    }

    public List<AgencyRouteTimestamp> convertFromSync(String feedId, String realtimeUrl) throws IOException {
        log.info("Reading realtime feed from id: {}, url: {}", feedId, realtimeUrl);
        var conn = ((HttpURLConnection) new URL(realtimeUrl).openConnection());
        if (conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
            log.info("Redirected id \"{}\" to \"{}\"", feedId, realtimeUrl);
            //call with new url, don't write the old one.
            return convertFromSync(feedId, conn.getHeaderField("Location"));
        } else if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            log.error("Failed to download static data from \"{}\", resCode \"\"", realtimeUrl);
            return Collections.emptyList();
        }
        try (var file = java.net.URI.create(realtimeUrl).toURL().openStream()) {
            GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(file);
            long timeStamp = feedMessage.getHeader().getTimestamp();
            List<AgencyRouteTimestamp> routeTimestampList = feedMessage.getEntityList().stream()
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(GtfsRealtimeParserService::validateRequiredFields)
                    .collect(Collectors.groupingBy(fe -> getRouteName(feedId, fe)))
                    .entrySet()
                    .stream()
                    .map(entry -> getAgencyRouteTimestamp(feedId, entry, timeStamp))
                    .toList();
            log.info("Read {} realtime feed entries from id: {}, url: {}", routeTimestampList.size(), feedId, realtimeUrl);
            return routeTimestampList;
        } catch (IOException e) {
            log.error("Error reading realtime feed from id: {}, url: {}", feedId, realtimeUrl, e);
            return Collections.emptyList();
        }

    }
}
