package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.repository.RoutesRepository;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRoute.UNKNOWN_ROUTE;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtResponseMapperService {
    private final RoutesRepository routesRepository;

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

    private static String getRouteName(String agencyId, GtfsRealtime.TripUpdate tripUpdate, RoutesRepository routesRepository) {
        String routeId = tripUpdate.getTrip().getRouteId();
        String routeName = routesRepository.getAgencyRoute(agencyId, routeId).orElse(UNKNOWN_ROUTE).getRouteName();
        if (StringUtils.isBlank(routeName)) {
            return UNKNOWN_ROUTE.getRouteName();
        }
        return routeName;
    }

    private static List<BusState> adaptBusStatesFrom(List<GtfsRealtime.TripUpdate> tripUpdate) {
        return tripUpdate.stream().map(RtResponseMapperService::adaptBusStateFrom).toList();
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
        return CompletableFuture.supplyAsync(() -> convertFromSync(agencyId, realtimeUrl));
    }

    public List<AgencyRouteTimestamp> convertFromSync(String agencyId, String realtimeUrl) {
        log.info("Reading realtime feed from id: {}, url: {}", agencyId, realtimeUrl);
        try (var file = java.net.URI.create(realtimeUrl).toURL().openStream()) {
            GtfsRealtime.FeedMessage feedMessage = GtfsRealtime.FeedMessage.parseFrom(file);
            long timeStamp = feedMessage.getHeader().getTimestamp();
            List<AgencyRouteTimestamp> routeTimestampList = feedMessage.getEntityList().stream()
                    .map(GtfsRealtime.FeedEntity::getTripUpdate)
                    .filter(RtResponseMapperService::validateRequiredFields)
                    .collect(Collectors.groupingBy(fe -> getRouteName(agencyId, fe, routesRepository)))
                    .entrySet()
                    .stream()
                    .map(entry -> getAgencyRouteTimestamp(agencyId, entry, timeStamp))
                    .toList();
            log.info("Read {} realtime feed entries from id: {}, url: {}", routeTimestampList.size(), agencyId, realtimeUrl);
            return routeTimestampList;
        } catch (IOException e) {
            log.error("Error reading realtime feed from id: {}, url: {}", agencyId, realtimeUrl, e);
            return Collections.emptyList();
        }

    }
}
