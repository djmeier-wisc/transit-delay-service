package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.service.GtfsRealtimeParserService.getFirstScheduled;

@Service
@RequiredArgsConstructor
public class ExpectedBusTimesService {
    private final AgencyFeedRepository agencyRepository;
    private final GtfsStaticRepository staticRepository;

    public Mono<ExpectedBusTimes> getTripMapFor(String feedId, Map<String, Integer> tripsWithoutDelayAttribute) {
        if (tripsWithoutDelayAttribute.isEmpty()) {
            return Mono.empty();
        }
        return Mono.zip(staticRepository.getTripMapFor(feedId, tripsWithoutDelayAttribute).collectList(), agencyRepository.getAgencyFeedById(feedId, false))
                .map(tuple -> {
                    var staticData = tuple.getT1();
                    var agencyData = tuple.getT2();
                    ExpectedBusTimes map = new ExpectedBusTimes();
                    for (GtfsStaticData data : staticData) {
                        map.putDeparture(data.getTripId(), data.getSequence(), data.getDepartureTime());
                        map.putArrival(data.getTripId(), data.getSequence(), data.getArrivalTime());
                    }
                    map.setTimezone(agencyData.getTimezone());
                    return map;
                });
    }

    public static Map<String, Integer> getTripsWithoutDelayAttribute(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .filter(ExpectedBusTimesService::doesTripNotHaveDelayAttribute)
                .filter(ExpectedBusTimesService::hasTripIdAndStopSequence)
                .filter(tu -> getFirstScheduled(tu).isPresent())
                .collect(Collectors.toMap(
                        ExpectedBusTimesService::getTripId,
                        tu -> getFirstScheduled(tu)
                                .map(GtfsRealtime.TripUpdate.StopTimeUpdate::getStopSequence)
                                .orElse(0),
                        (a, b) -> a));
    }

    private static boolean hasTripIdAndStopSequence(GtfsRealtime.TripUpdate tripUpdate) {
        return tripUpdate.getTrip().hasTripId() && tripUpdate.getStopTimeUpdateCount() > 0;
    }

    private static boolean doesTripNotHaveDelayAttribute(GtfsRealtime.TripUpdate tripUpdate) {
        return !(tripUpdate.hasDelay() ||
                tripUpdate.getStopTimeUpdateList().stream().anyMatch(s -> s.getDeparture().hasDelay()) ||
                tripUpdate.getStopTimeUpdateList().stream().anyMatch(s -> s.getArrival().hasDelay()));
    }

    private static String getTripId(GtfsRealtime.TripUpdate tripUpdate) {
        return tripUpdate.getTrip().getTripId();
    }

    public Mono<ExpectedBusTimes> getTripMapFor(String feedId, List<GtfsRealtime.TripUpdate> tripUpdates) {
        return getTripMapFor(feedId, getTripsWithoutDelayAttribute(tripUpdates));
    }
}
