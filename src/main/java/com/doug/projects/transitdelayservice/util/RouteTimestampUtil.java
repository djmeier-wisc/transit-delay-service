package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static java.lang.Math.abs;
import static java.lang.Math.floor;

public class RouteTimestampUtil {
    public static Double getMaxDelayForRouteInMinutes(List<RouteTimestamp> timestampsForRoute) {

        OptionalInt averageDelay =
                timestampsForRoute.stream().mapToInt(RouteTimestampUtil::getMaxDelayFromBusStatesList).max();
        if (averageDelay.isEmpty()) {
            return null;
        }
        double timeInMinutes = ((double) averageDelay.getAsInt()) / 60; //convert to minutes
        return floor(timeInMinutes * 1000) / 1000;
    }

    public static Double percentOnTime(List<RouteTimestamp> timestampsForRoute, Integer lower, Integer upper) {

        List<Integer> allBusStates = timestampsForRoute.stream()
                .flatMap(rt -> rt.getBusStatesList().stream().map(BusState::fromString))
                .map(BusState::getDelay).toList();

        Double percentOnTime =
                ((double) allBusStates.stream().filter(delay -> delay / 60 >= lower && delay / 60 <= upper).count() /
                        allBusStates.size()) * 100;
        if (allBusStates.isEmpty()) {
            return null;
        }
        return percentOnTime;

    }

    public static Integer getMaxDelayFromBusStatesList(RouteTimestamp rt) {
        return rt.getBusStatesList().stream().map(bs -> {
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
        }).filter(Objects::nonNull).max(Integer::compareTo).orElse(-1);
    }

    public static Double getAverageDelayDataForRouteInMinutes(List<RouteTimestamp> timestampsForRoute) {

        OptionalDouble averageDelay =
                timestampsForRoute.stream().mapToDouble(RouteTimestamp::getAverageDelay).average();
        if (averageDelay.isEmpty()) {
            return null;
        }
        double timeInMinutes = averageDelay.getAsDouble() / 60; //convert to minutes
        return floor(timeInMinutes * 1000) / 1000;
    }
}
