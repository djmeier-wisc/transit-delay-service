package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.entity.transit.ExpectedBusTimes;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.springframework.util.CollectionUtils.isEmpty;

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
                        if (isEmpty(data.getSequencedData())) continue;
                        var optionalSequencedData = data.getSequencedData().stream().filter(s -> s.getSequenceNo() == tripsWithoutDelayAttribute.get(data.getTripId())).findFirst();
                        if (optionalSequencedData.isEmpty()) continue;
                        var sequencedData = optionalSequencedData.get();
                        map.putDeparture(data.getTripId(), sequencedData.getSequenceNo(), sequencedData.getDepartureTime());
                        map.putArrival(data.getTripId(), sequencedData.getSequenceNo(), sequencedData.getArrivalTime());
                    }
                    map.setTimezone(agencyData.getTimezone());
                    return map;
                });
    }
}
