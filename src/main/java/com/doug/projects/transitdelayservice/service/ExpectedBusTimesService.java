package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopTime;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopTimeId;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyStopTimeRepository;
import com.google.transit.realtime.GtfsRealtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.service.GtfsRealtimeParserService.getFirstScheduled;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpectedBusTimesService {
    private final AgencyFeedRepository agencyRepository;
    private final AgencyStopTimeRepository agencyStopTimeRepository;

    public ExpectedBusTimes getTripMapFor(String feedId, Map<String, Integer> tripsWithoutDelayAttribute) {
        if (tripsWithoutDelayAttribute.isEmpty()) {
            return new ExpectedBusTimes();
        }
        List<AgencyStopTimeId> ids = tripsWithoutDelayAttribute.entrySet().stream()
                .map(e -> new AgencyStopTimeId(e.getKey(), e.getValue(), feedId))
                        .toList();
        List<AgencyStopTime> agencyStopTimes = agencyStopTimeRepository.findAllByIdInAndTripRouteAgencyId(ids,feedId);
        Optional<AgencyFeed> feed = agencyRepository.findById(feedId);
        var busTimes = new ExpectedBusTimes();
        for (AgencyStopTime i : agencyStopTimes) {
            busTimes.putDeparture(i.getTripId(), i.getStopSeq(), i.getDepartureTimeSecs());
            busTimes.putArrival(i.getTripId(), i.getStopSeq(), i.getArrivalTimeSecs());
            busTimes.setTimezone(feed.map(AgencyFeed::getTimezone).orElse(null));
        }
        var foundIds = agencyStopTimes.stream().map(AgencyStopTime::getId).collect(Collectors.toSet());
        var notFoundIds = ids.stream()
                .filter(s -> !foundIds.contains(s))
                .toList();
        if (!notFoundIds.isEmpty()) {
            log.error("Failed to find ids: {}", notFoundIds);
        }
        return busTimes;
    }

    public static Map<String, Integer> getTripsWithoutDelayAttribute(List<GtfsRealtime.TripUpdate> tripUpdates) {
        return tripUpdates.stream()
                .filter(tu -> getFirstScheduled(tu).isPresent())
                .filter(tu -> StringUtils.isNotBlank(tu.getTrip().getTripId()))
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

    public ExpectedBusTimes getTripMapFor(String feedId, List<GtfsRealtime.TripUpdate> tripUpdates) {
        return getTripMapFor(feedId, getTripsWithoutDelayAttribute(tripUpdates));
    }
}
