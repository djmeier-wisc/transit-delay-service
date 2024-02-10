package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.TimeBusState;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static java.lang.Math.floor;

public class RouteTimestampUtil {
    public static Double getMaxDelayForRouteInMinutes(List<RouteTimestamp> timestampsForRoute) {

        OptionalInt averageDelay = timestampsForRoute.stream()
                .mapToInt(RouteTimestampUtil::getMaxDelayFromBusStatesList)
                .max();
        if (averageDelay.isEmpty()) {
            return null;
        }
        double timeInMinutes = ((double) averageDelay.getAsInt()) / 60; //convert to minutes
        return floor(timeInMinutes * 1000) / 1000;
    }

    public static Double percentOnTime(List<RouteTimestamp> timestampsForRoute, Integer lower, Integer upper) {

        List<Integer> allBusStates = timestampsForRoute.stream()
                .flatMap(rt -> rt.getBusStatesList()
                        .stream()
                        .map(RouteTimestampUtil::extractBusStates))
                .map(BusState::getDelay)
                .toList();

        Double percentOnTime =
                ((double) allBusStates.stream().filter(delay -> delay / 60 >= lower && delay / 50 <= upper).count() /
                        allBusStates.size()) * 100;
        if (allBusStates.isEmpty()) {
            return null;
        }
        return percentOnTime;

    }

    public static Integer getMaxDelayFromBusStatesList(RouteTimestamp rt) {
        return rt.getBusStatesList()
                .stream()
                .map(bs -> {
                    String[] vals = StringUtils.split(bs, "#");
                    //see BusStatesList.java for code on how these are serialized. There was probably a
                    // better solution, but I didn't figure it out.
                    if (vals.length < 1) {
                        return null;
                    }
                    try {
                        return abs(Integer.parseInt(vals[0]));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
    }

    /**
     * Deserializes busStates object from stringToParse.
     *
     * @param stringToParse string representation of busStates object. See toString of BusState
     * @return busStates object deserialized from stringToParse.
     */
    public static BusState extractBusStates(String stringToParse) {
        BusState busStates = new BusState();
        String[] vals = stringToParse.split("#");
        Integer delay = vals.length < 1 ? null : Integer.valueOf(vals[0]);
        Integer closestStopId = vals.length < 2 ? null : Integer.valueOf(vals[1]);
        Integer tripId = vals.length < 3 ? null : Integer.valueOf(vals[2]);
        busStates.setDelay(delay);
        busStates.setClosestStopId(closestStopId);
        busStates.setTripId(tripId);
        return busStates;
    }

    public static Double getAverageDelayDataForRouteInMinutes(List<RouteTimestamp> timestampsForRoute) {

        OptionalDouble averageDelay = timestampsForRoute.stream()
                .mapToDouble(RouteTimestamp::getAverageDelay)
                .average();
        if (averageDelay.isEmpty()) {
            return null;
        }
        double timeInMinutes = averageDelay.getAsDouble() / 60; //convert to minutes
        return floor(timeInMinutes * 1000) / 1000;
    }

    public static Stream<TimeBusState> getTimeBusState(RouteTimestamp routeTimestamp) {
        return routeTimestamp.getBusStatesList()
                .stream()
                .map(stringToParse -> {
                    TimeBusState timeBusState = new TimeBusState();
                    String[] vals = stringToParse.split("#");
                    Integer delay = vals.length < 1 ? null : Integer.valueOf(vals[0]);
                    Integer closestStopId = vals.length < 2 ? null : Integer.valueOf(vals[1]);
                    Integer tripId = vals.length < 3 ? null : Integer.valueOf(vals[2]);
                    timeBusState.setTimestamp(routeTimestamp.getTimestamp());
                    timeBusState.setDelay(delay);
                    timeBusState.setClosestStopId(closestStopId);
                    timeBusState.setTripId(tripId);
                    return timeBusState;
                });
    }
}
