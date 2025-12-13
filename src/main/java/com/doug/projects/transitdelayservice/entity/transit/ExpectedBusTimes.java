package com.doug.projects.transitdelayservice.entity.transit;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps departures and arrivals to their respective scheduled times.
 */
public class ExpectedBusTimes {
    private final Map<String, Map<Integer, Integer>> departureMap = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> arrivalMap = new HashMap<>();
    @Getter
    @Setter
    private String timezone;

    public void putDeparture(String tripId, Integer stopSequence, Integer time) {
        if (departureMap.containsKey(tripId)) {
            departureMap.get(tripId).put(stopSequence, time);
        } else {
            Map<Integer, Integer> newMap = new HashMap<>();
            newMap.put(stopSequence, time);
            departureMap.put(tripId, newMap);
        }
    }

    public void putArrival(String tripId, Integer stopSequence, Integer time) {
        if (arrivalMap.containsKey(tripId)) {
            arrivalMap.get(tripId).put(stopSequence, time);
        } else {
            Map<Integer, Integer> newMap = new HashMap<>();
            newMap.put(stopSequence, time);
            arrivalMap.put(tripId, newMap);
        }
    }

    public Optional<Integer> getDepartureTime(String tripId, Integer stopSequence) {
        var stopMap = departureMap.get(tripId);
        if (stopMap == null) {
            return Optional.empty();
        }
        var departureTime = stopMap.get(stopSequence);
        if (departureTime == null) {
            return Optional.empty();
        }
        return Optional.of(departureTime);
    }

    public Optional<Integer> getArrivalTime(String tripId, Integer stopSequence) {
        var stopMap = arrivalMap.get(tripId);
        if (stopMap == null) {
            return Optional.empty();
        }
        var departureTime = stopMap.get(stopSequence);
        if (departureTime == null) {
            return Optional.empty();
        }
        return Optional.of(departureTime);
    }
}
