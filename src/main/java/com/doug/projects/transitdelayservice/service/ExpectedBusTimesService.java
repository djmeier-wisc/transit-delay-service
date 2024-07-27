package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpectedBusTimesService {
    private final AgencyFeedRepository agencyRepository;
    private final GtfsStaticRepository staticRepository;

    public Mono<ExpectedBusTimes> getTripMapFor(String feedId, Map<String, Integer> tripsWithoutDelayAttribute) {
        if (tripsWithoutDelayAttribute.isEmpty()) {
            return Mono.empty();
        }
        return Mono.zip(staticRepository.getTripMapFor(feedId, tripsWithoutDelayAttribute).collectList(), agencyRepository.getAgencyFeedById(feedId))
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
}
