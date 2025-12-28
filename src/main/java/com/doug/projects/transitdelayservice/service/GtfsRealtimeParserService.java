package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeAnalysisResponse;
import com.doug.projects.transitdelayservice.entity.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.BusState;
import com.doug.projects.transitdelayservice.entity.Status;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyRouteId;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripId;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyRouteRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyTripRepository;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import com.google.transit.realtime.GtfsRealtime;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimeParserService {
    private final ExpectedBusTimesService expectedBusTimesService;
    private final AgencyRouteRepository agencyRouteRepository;
    private final AgencyTripRepository agencyTripRepository;
    private final RestTemplate restTemplate;

    /**
     * Validate the required fields for Entity mapping
     *
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(GtfsRealtime.TripUpdate entity) {
        return entity.getTrip().getScheduleRelationship().equals(SCHEDULED) && getFirstScheduled(entity).isPresent() && entity.hasTrip() && entity.getTrip().hasTripId();
    }

    @NotNull
    private Optional<BusState> adaptBusStateFrom(GtfsRealtime.TripUpdate tripUpdate, ExpectedBusTimes tripMap) {
        if (getFirstScheduled(tripUpdate).isEmpty())
            return Optional.empty();
        OptionalInt delay = getDelay(tripUpdate, tripMap);
        if (delay.isEmpty())
            return Optional.empty();
        BusState busState = new BusState();
        busState.setTripId(tripUpdate.getTrip().getTripId());
        busState.setDelay(delay.getAsInt());
        busState.setClosestStopId(getFirstScheduled(tripUpdate).get().getStopId());
        return Optional.of(busState);
    }

    /**
     * This method extracts delay from a tripUpdate. Note that it will first try to get departureDelay,
     * then arrivalDelay, then departureTime (diffed with schedule), then arrivalTime (diffed with schedule).
     * Will return null in the case of not finding anything in the schedule, or if no details are passed.
     *
     * @param tu      the tripUpdate
     * @param tripMap the tripIds and their associated arrival/departure times
     * @return the delay of the trip, or null if the trip data was not found in tripMap / the feed was invalid
     */
    private OptionalInt getDelay(GtfsRealtime.TripUpdate tu, ExpectedBusTimes tripMap) {
        if (tu.hasDelay()) {
            return OptionalInt.of(tu.getDelay());
        }
        var departureDelay = getDepartureDelay(tu);
        if (departureDelay.isPresent()) {
            return OptionalInt.of(departureDelay.get());
        }
        var arrivalDelay = getArrivalDelay(tu);
        if (arrivalDelay.isPresent()) {
            return OptionalInt.of(arrivalDelay.get());
        }
        if (tripMap == null) {
            log.error("TripMap was unexpectedly null!");
            return OptionalInt.empty();
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

    /**
     * Prefers diffing delay based on departureTime, but works with arrival too.
     *
     * @param tu      the tripUpdate to extract the actual departure/arrival time from rt
     * @param tripMap used to get the expected departure/arrival time from schedule
     * @return empty if there are no SCHEDULED relationship tripUpdates, or if it has no departure/arrival time. Otherwise, parses the diff between scheduled and actual departure/arrival
     */
    private OptionalInt extractDifferenceFromActualAndExpectedTime(GtfsRealtime.TripUpdate tu, ExpectedBusTimes tripMap) {
        var optionalStopTimeUpdate = getFirstScheduled(tu);
        if (optionalStopTimeUpdate.isEmpty()) {
            return OptionalInt.empty();
        }
        var currStopTimeUpdate = optionalStopTimeUpdate.get();
        var arrival = currStopTimeUpdate.getArrival();
        var departure = currStopTimeUpdate.getDeparture();
        var timezone = tripMap.getTimezone();
        try {
            var expectedDeparture = tripMap.getDepartureTime(tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());
            if (expectedDeparture.isEmpty()) {
                log.error("TripMap did not contain dept {} trip sq {}", tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());
            }
            var actualDeparture = departure.getTime();
            var actualArrival = arrival.getTime();
            var expectedArrival = tripMap.getArrivalTime(tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());

            if (currStopTimeUpdate.hasDeparture() && departure.hasTime() && isNotEmpty(expectedDeparture) && isNotEmpty(timezone) && expectedDeparture.isPresent()) {
                return OptionalInt.of(TransitDateUtil.calculateTimeDifferenceInSeconds(expectedDeparture.get(), actualDeparture, timezone));
            } else if (currStopTimeUpdate.hasArrival() && arrival.hasTime() && isNotEmpty(expectedArrival) && isNotEmpty(timezone) && expectedArrival.isPresent()) {
                return OptionalInt.of(TransitDateUtil.calculateTimeDifferenceInSeconds(expectedArrival.get(), actualArrival, timezone));
            }
        } catch (DateTimeParseException parseException) {
            log.error("Unable to parse date for tripUpdate: {}", tu);
        }
        return OptionalInt.empty();
    }

    static Optional<GtfsRealtime.TripUpdate.StopTimeUpdate> getFirstScheduled(GtfsRealtime.TripUpdate tripUpdate) {
        return tripUpdate.getStopTimeUpdateList()
                .stream()
                .filter(tu ->
                        tu.getScheduleRelationship()
                                .equals(GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED))
                .findFirst();
    }

    private static AgencyRealtimeAnalysisResponse buildTimeoutFailureResponse(AgencyFeedDto feed) {
        return AgencyRealtimeAnalysisResponse.builder().feedStatus(Status.TIMEOUT).feed(feed).build();
    }

    private static AgencyRealtimeAnalysisResponse buildUnauthorizedFailureResponse(AgencyFeedDto feed) {
        return AgencyRealtimeAnalysisResponse.builder().feedStatus(Status.UNAUTHORIZED).feed(feed).build();
    }

    private static AgencyRealtimeAnalysisResponse buildUnavailableFailureResponse(AgencyFeedDto feed) {
        return AgencyRealtimeAnalysisResponse.builder().feedStatus(Status.UNAVAILABLE).feed(feed).build();
    }

    private Optional<String> getRouteName(GtfsRealtime.TripUpdate tripUpdate, String agencyId) {
        return Optional.ofNullable(tripUpdate.getTrip().getRouteId())
                .filter(StringUtils::isNotBlank)
                .map(routeId -> new AgencyRouteId(routeId, agencyId))
                .flatMap(agencyRouteRepository::findRouteNameById)
                .or(()->Optional.ofNullable(tripUpdate.getTrip().getTripId())
                        .map(tripId -> new AgencyTripId(tripId, agencyId))
                        .flatMap(agencyTripRepository::findRouteNameById));
    }

    public AgencyRealtimeAnalysisResponse pollFeed(AgencyFeedDto feed) {
        try {
            var url = new URL(feed.getRealTimeUrl());
            var rtResp = GtfsRealtime.FeedMessage.parseFrom(url.openStream());
            return getAgencyRealtimeResponse(feed, rtResp);
        } catch (MalformedURLException ex) {
            log.error("Feed {} unsupported",feed.getId(), ex);
            return buildUnauthorizedFailureResponse(feed);
        } catch (ResourceAccessException ex) {
            log.error("Feed {} timed out", feed.getId(), ex);
            return buildTimeoutFailureResponse(feed);
        } catch (RuntimeException | IOException ex) {
            log.error("Feed {} runtime issue", feed.getId(), ex);
            return buildUnavailableFailureResponse(feed);
        }
    }

    private AgencyRealtimeAnalysisResponse getAgencyRealtimeResponse(AgencyFeedDto feed, GtfsRealtime.FeedMessage message) {
        long timestamp = message.getHeader().getTimestamp();
        String feedId = feed.getId();
        List<GtfsRealtime.TripUpdate> tripUpdates = message.getEntityList().stream()
                .map(GtfsRealtime.FeedEntity::getTripUpdate)
                .toList();
        return getAgencyRealtimeResponse(feed,message, expectedBusTimesService.getTripMapFor(feedId, tripUpdates), timestamp);
    }

    private AgencyRealtimeAnalysisResponse getAgencyRealtimeResponse(AgencyFeedDto feed,
                                                                     GtfsRealtime.FeedMessage message,
                                                                     ExpectedBusTimes expectedBusTimes,
                                                                     long timestamp) {
        String feedId = feed.getId();
        Map<String, List<GtfsRealtime.TripUpdate>> routeNameToTripUpdateMap = new HashMap<>();
        for (GtfsRealtime.FeedEntity e : message.getEntityList()) {
            GtfsRealtime.TripUpdate tu = e.getTripUpdate();
            if (!validateRequiredFields(tu)) {
                continue;
            }
            Optional<String> routeName = getRouteName(tu, feedId);
            if (routeName.isEmpty()) {
                log.error("Unable to get routeName! Returning as outdated for {}", feed.getId());
                return AgencyRealtimeAnalysisResponse.builder()
                        .feed(feed)
                        .feedStatus(Status.OUTDATED)
                        .build();
            }
            routeNameToTripUpdateMap.computeIfAbsent(routeName.get(), k -> new ArrayList<>()).add(tu);
        }
        List<AgencyRouteTimestamp> routeTimestampList = routeNameToTripUpdateMap
                .entrySet()
                .stream()
                .map(entry -> getAgencyRouteTimestamp(feedId, entry, timestamp, expectedBusTimes))
                .toList();
        if (containsNullDelay(routeTimestampList)) {
            log.error("Feed {} had null delay!", feedId);
            return AgencyRealtimeAnalysisResponse.builder()
                    .feed(feed)
                    .feedStatus(Status.OUTDATED)
                    .routeTimestamps(filterNullDelay(routeTimestampList))
                    .build();
        }
        return AgencyRealtimeAnalysisResponse.builder()
                .feed(feed)
                .feedStatus(Status.ACTIVE)
                .routeTimestamps(routeTimestampList)
                .build();
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
        return tripUpdates.stream()
                .map(tripUpdate -> adaptBusStateFrom(tripUpdate, tripMap))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private boolean containsNullDelay(List<AgencyRouteTimestamp> routeTimestampList) {
        return routeTimestampList.stream()
                .anyMatch(r -> isEmpty(r.getBusStatesCopyList()) || r.getBusStatesCopyList().stream().map(BusState::getDelay).anyMatch(Objects::isNull));
    }

    private List<AgencyRouteTimestamp> filterNullDelay(List<AgencyRouteTimestamp> routeTimestampList) {
        return routeTimestampList.stream().filter(r ->
                isNotEmpty(r.getBusStatesCopyList()) && r.getBusStatesCopyList().stream().map(BusState::getDelay).noneMatch(Objects::isNull)
        ).toList();
    }
}
