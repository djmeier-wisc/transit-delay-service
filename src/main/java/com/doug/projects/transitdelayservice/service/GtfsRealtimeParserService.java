package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.AgencyRealtimeResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.http.codec.protobuf.ProtobufEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.SCHEDULED;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class GtfsRealtimeParserService {
    private final ExpectedBusTimesService expectedBusTimesService;
    private final GtfsStaticRepository staticRepo;

    /**
     * Validate the required fields for Entity mapping
     *
     * @param entity the entity to check
     * @return true if all required fields are not null
     */
    private static boolean validateRequiredFields(GtfsRealtime.TripUpdate entity) {
        return entity.getTrip().getScheduleRelationship().equals(SCHEDULED) && getFirstScheduled(entity).isPresent();
    }

    @NotNull
    private static Optional<BusState> adaptBusStateFrom(GtfsRealtime.TripUpdate tripUpdate, ExpectedBusTimes tripMap) {
        if (tripUpdate.getStopTimeUpdateCount() <= 0)
            return Optional.empty();
        OptionalInt delay = getDelay(tripUpdate, tripMap);
        if (delay.isEmpty())
            return Optional.empty();
        BusState busState = new BusState();
        busState.setTripId(tripUpdate.getTrip().getTripId());
        busState.setDelay(delay.getAsInt());
        busState.setClosestStopId(tripUpdate.getStopTimeUpdate(0).getStopId());
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
    private static OptionalInt getDelay(GtfsRealtime.TripUpdate tu, ExpectedBusTimes tripMap) {
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
    private static OptionalInt extractDifferenceFromActualAndExpectedTime(GtfsRealtime.TripUpdate tu, ExpectedBusTimes tripMap) {
        var optionalStopTimeUpdate = getFirstScheduled(tu);
        if (optionalStopTimeUpdate.isEmpty()) {
            return OptionalInt.empty();
        }
        var currStopTimeUpdate = optionalStopTimeUpdate.get();
        var arrival = currStopTimeUpdate.getArrival();
        var departure = currStopTimeUpdate.getDeparture();
        var timezone = tripMap.getTimezone();
        try {
            if (currStopTimeUpdate.hasDeparture() && departure.hasTime()) {
                var actualArrival = departure.getTime();
                var expectedArrival = tripMap.getDepartureTime(tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());
                if (isEmpty(expectedArrival) || isEmpty(timezone) || expectedArrival.isEmpty()) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(TransitDateUtil.calculateTimeDifferenceInSeconds(expectedArrival.get(), actualArrival, timezone));
            } else if (currStopTimeUpdate.hasArrival() && arrival.hasTime()) {
                var actualArrival = arrival.getTime();
                var expectedArrival = tripMap.getArrivalTime(tu.getTrip().getTripId(), currStopTimeUpdate.getStopSequence());
                if (isEmpty(expectedArrival) || isEmpty(timezone) || expectedArrival.isEmpty()) {
                    return OptionalInt.empty();
                }
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

    private static AgencyRealtimeResponse buildTimeoutFailureResponse(AgencyFeed feed) {
        return AgencyRealtimeResponse.builder().feedStatus(AgencyFeed.Status.TIMEOUT).feed(feed).build();
    }

    private static AgencyRealtimeResponse buildUnauthorizedFailureResponse(AgencyFeed feed) {
        return AgencyRealtimeResponse.builder().feedStatus(AgencyFeed.Status.UNAUTHORIZED).feed(feed).build();
    }

    private static AgencyRealtimeResponse buildUnavailableFailureResponse(AgencyFeed feed) {
        return AgencyRealtimeResponse.builder().feedStatus(AgencyFeed.Status.UNAVAILABLE).feed(feed).build();
    }

    private Optional<String> getRouteName(GtfsRealtime.TripUpdate tripUpdate, Map<String, String> tripMap, Map<String, String> routeMap) {
        String routeId = tripUpdate.getTrip().getRouteId();
        if (StringUtils.isNotBlank(routeId) && routeMap.containsKey(routeId)) {
            return Optional.ofNullable(routeMap.get(routeId));

        }
        String tripId = tripUpdate.getTrip().getTripId();
        if (StringUtils.isNotBlank(tripId) && tripMap.containsKey(tripId)) {
            return Optional.ofNullable(tripMap.get(tripId));
        }
        return Optional.empty();
    }

    public Mono<AgencyRealtimeResponse> convertFromAsync(AgencyFeed feed, int timeoutSeconds) {
        var client = WebClient.builder().baseUrl(feed.getRealTimeUrl()).codecs(c -> {
            c.customCodecs().register(new ProtobufDecoder());
            c.customCodecs().register(new ProtobufEncoder());
        }).build();
        return client.get()
                .accept(MediaType.APPLICATION_PROTOBUF)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    try {
                        return GtfsRealtime.FeedMessage.parseFrom(bytes); // Parse Protobuf manually. I can't get this to work otherwise
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException("Failed to parse Protobuf response", e);
                    }
                })
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
                .flatMap(message -> getAgencyRealtimeResponse(feed, message))
                .onErrorReturn(TimeoutException.class, buildTimeoutFailureResponse(feed))
                .onErrorReturn(UnsupportedMediaTypeException.class, buildUnauthorizedFailureResponse(feed))
                .onErrorReturn(RuntimeException.class, buildUnavailableFailureResponse(feed));
    }

    private Mono<AgencyRealtimeResponse> getAgencyRealtimeResponse(AgencyFeed feed, GtfsRealtime.FeedMessage message) {
        long timestamp = message.getHeader().getTimestamp();
        String feedId = feed.getId();
        List<GtfsRealtime.TripUpdate> tripUpdates = message.getEntityList().stream()
                .map(GtfsRealtime.FeedEntity::getTripUpdate)
                .toList();
        return Mono.zip(expectedBusTimesService.getTripMapFor(feedId, tripUpdates), staticRepo.getTripIdsAndRouteIds(feedId, getTripIds(tripUpdates), getRouteIds(tripUpdates)).collectList())
                .map(staticData -> getAgencyRealtimeResponse(feed, message, staticData.getT1(), staticData.getT2(), timestamp));
    }

    private List<String> getRouteIds(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .map(GtfsRealtime.TripUpdate::getTrip)
                .filter(Objects::nonNull)
                .map(GtfsRealtime.TripDescriptor::getRouteId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private List<String> getTripIds(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .map(GtfsRealtime.TripUpdate::getTrip)
                .filter(Objects::nonNull)
                .map(GtfsRealtime.TripDescriptor::getTripId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private AgencyRealtimeResponse getAgencyRealtimeResponse(AgencyFeed feed,
                                                             GtfsRealtime.FeedMessage message,
                                                             ExpectedBusTimes expectedBusTimes,
                                                             List<GtfsStaticData> staticData, long timestamp) {
        Map<String, String> tripIdToRouteNameMap = staticData.stream().collect(Collectors.toMap(GtfsStaticData::getTripId, GtfsStaticData::getRouteName, (a, b) -> a));
        Map<String, String> routeIdToRouteNameMap = staticData.stream().collect(Collectors.toMap(GtfsStaticData::getId, GtfsStaticData::getRouteName, (a, b) -> a));
        String feedId = feed.getId();
        Map<String, List<GtfsRealtime.TripUpdate>> routeNameToTripUpdateMap = new HashMap<>();
        for (GtfsRealtime.FeedEntity e : message.getEntityList()) {
            GtfsRealtime.TripUpdate tu = e.getTripUpdate();
            if (!validateRequiredFields(tu)) {
                continue;
            }
            Optional<String> routeName = getRouteName(tu, tripIdToRouteNameMap, routeIdToRouteNameMap);
            if (routeName.isEmpty()) {
                log.error("Unable to get routeName! Returning as outdated for {}", feed.getId());
                return AgencyRealtimeResponse.builder()
                        .feed(feed)
                        .feedStatus(AgencyFeed.Status.OUTDATED)
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
            return AgencyRealtimeResponse.builder()
                    .feed(feed)
                    .feedStatus(AgencyFeed.Status.OUTDATED)
                    .routeTimestamps(filterNullDelay(routeTimestampList))
                    .build();
        }
        return AgencyRealtimeResponse.builder()
                .feed(feed)
                .feedStatus(AgencyFeed.Status.ACTIVE)
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
