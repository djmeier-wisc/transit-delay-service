package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.BusState;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopId;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelay;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyStopRepository;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyTripDelayDto;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyTripDelayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@Slf4j
@RequiredArgsConstructor
public class AgencyRouteTimestampRepository {

    private final AgencyStopRepository agencyStopRepository;
    private final AgencyTripDelayRepository agencyTripDelayRepository;

    /**
     * Writes all items in data to table synchronously.
     *
     * @param data the data to save
     * @param agencyId
     */
    public void saveAll(List<AgencyRouteTimestamp> data, String agencyId) {
        var stopIds = data.stream()
                .map(AgencyRouteTimestamp::getBusStatesCopyList)
                .flatMap(Collection::stream)
                .map(BusState::getClosestStopId)
                .map(stopId -> new AgencyStopId(stopId, agencyId))
                .collect(Collectors.toSet());

        var dbStopIds = agencyStopRepository.findStopIdByIdIn(stopIds);

        if (dbStopIds.size() != stopIds.size()) {
            var missingStopIds = stopIds.stream()
                    .map(AgencyStopId::getStopId)
                    .filter(stopId -> !dbStopIds.contains(stopId))
                    .collect(Collectors.toSet());
            log.error("Failed to pull stopIds {}", missingStopIds);
        }

        var entities = data.stream()
                .flatMap(rt ->
                        rt.getBusStatesCopyList().stream()
                                .filter(busState -> dbStopIds.contains(busState.getClosestStopId()))
                                .map(busState ->
                                        AgencyTripDelay.builder()
                                                .tripId(busState.getTripId())
                                                .agencyId(agencyId)
                                                .stopId(busState.getClosestStopId())
                                                .timestamp(rt.getTimestamp())
                                                .delaySeconds(busState.getDelay())
                                                .build()

                                )
                ).toList();
        for (AgencyTripDelay entity : entities) {
            try {
                agencyTripDelayRepository.save(entity);
            } catch (Exception e) {
                log.error("Failed to save w/ e", e);
            }
        }
        log.info("Saving {} tripDelays", entities.size());
    }

    public List<AgencyRouteTimestamp> getRouteTimestampsBy(long startTime, long endTime, List<String> routeNames, String feedId) {

        var delayByRouteName = agencyTripDelayRepository.findDelayRecordsForRoutesAndTimeRange(feedId, routeNames, startTime, endTime)
                .stream()
                .collect(Collectors.groupingBy(AgencyTripDelayDto::routeName,
                        Collectors.groupingBy(AgencyTripDelayDto::timestamp)));
        List<AgencyRouteTimestamp> routeTimestamps = new ArrayList<>();
        delayByRouteName.forEach((name, map) -> {
            map.forEach((timestamp, delays) -> {
                var routeTimestamp = new AgencyRouteTimestamp();
                routeTimestamp.setTimestamp(timestamp);
                routeTimestamp.setAgencyRoute(feedId, name);
                var busStates = delays.stream()
                        .map(delay -> BusState.builder()
                                .delay(delay.delaySeconds())
                                .closestStopId(delay.stopId())
                                .tripId(delay.tripId())
                                .build())
                        .toList();
                routeTimestamp.setBusStates(busStates);
                routeTimestamps.add(routeTimestamp);
            });
        });
        return routeTimestamps;
    }
}
