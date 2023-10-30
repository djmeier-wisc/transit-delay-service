package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.BusStates;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.abs;
import static java.lang.Math.floor;

public class RouteTimestampUtil {
    public static List<Double> getMaxDelayForRouteInMinutes(Long startTime, Integer units, double perUnitSecondLength
            , List<RouteTimestamp> timestampsForRoute) {
        List<Double> currData = new ArrayList<>(units);
        int lastIndexUsed = 0;
        for (int currUnit = 0; currUnit < units; currUnit++) {
            final long finalCurrEndTime = (long) (startTime + (perUnitSecondLength * (currUnit + 1)));
            int currLastIndex = timestampsForRoute.size();
            for (int i = lastIndexUsed; i < timestampsForRoute.size(); i++) {
                if (timestampsForRoute.get(i).getTimestamp() >= finalCurrEndTime) {
                    currLastIndex = i;
                    break;
                }
            }
            OptionalInt averageDelay = timestampsForRoute.subList(lastIndexUsed, currLastIndex).stream()
                    .mapToInt(RouteTimestampUtil::getMaxDelayFromBusStatesList).max();
            if (averageDelay.isPresent()) {
                double timeInMinutes = ((double) averageDelay.getAsInt()) / 60; //convert to minutes
                currData.add(floor(timeInMinutes * 1000) / 1000);
            } else {
                currData.add(null);
            }
            //get ready for next iteration
            lastIndexUsed = currLastIndex;
        }
        return currData;
    }

    public static List<Double> percentOnTime(Long startTime, Long endTime, Integer units,
                                             List<RouteTimestamp> timestampsForRoute, Integer criteria) {
        List<Double> currData = new ArrayList<>(units);
        double perUnitSecondLength = (double) (endTime - startTime) / units;
        int lastIndexUsed = 0;
        for (int currUnit = 0; currUnit < units; currUnit++) {
            final long finalCurrEndTime = (long) (startTime + (perUnitSecondLength * (currUnit + 1)));
            int currLastIndex = timestampsForRoute.size();
            for (int i = lastIndexUsed; i < timestampsForRoute.size(); i++) {
                if (timestampsForRoute.get(i).getTimestamp() >= finalCurrEndTime) {
                    currLastIndex = i;
                    break;
                }
            }
            List<Integer> allBusStates = timestampsForRoute.subList(lastIndexUsed, currLastIndex).stream()
                    .flatMap(rt -> rt.getBusStatesList().stream().map(RouteTimestampUtil::extractBusStates))
                    .map(BusStates::getDelay).collect(Collectors.toList());

            Double percentOnTime =
                    ((double) allBusStates.stream().filter(delay -> Math.abs(delay) / 60 <= criteria).count() /
                            allBusStates.size()) * 100;
            if (allBusStates.isEmpty()) {
                currData.add(null);
            } else {
                currData.add(percentOnTime);
            }
            //get ready for next iteration
            lastIndexUsed = currLastIndex;
        }
        return currData;
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

    /**
     * Deserializes busStates object from stringToParse.
     *
     * @param stringToParse string representation of busStates object. See toString of BusStates
     * @return busStates object deserialized from stringToParse.
     */
    public static BusStates extractBusStates(String stringToParse) {
        BusStates busStates = new BusStates();
        String[] vals = stringToParse.split("#");
        Integer delay = vals.length < 1 ? null : Integer.valueOf(vals[0]);
        String closestStopId = vals.length < 2 ? null : vals[1];
        Integer tripId = vals.length < 3 ? null : Integer.valueOf(vals[2]);
        busStates.setDelay(delay);
        busStates.setClosestStopId(closestStopId);
        busStates.setTripId(tripId);
        return busStates;
    }

    public static List<Double> getAverageDelayDataForRouteInMinutes(Long startTime, Integer units,
                                                                    double perUnitSecondLength,
                                                                    List<RouteTimestamp> timestampsForRoute) {
        List<Double> currData = new ArrayList<>(units);
        int lastIndexUsed = 0;
        for (int currUnit = 0; currUnit < units; currUnit++) {
            final long finalCurrEndTime = (long) (startTime + (perUnitSecondLength * (currUnit + 1)));
            int currLastIndex = timestampsForRoute.size();
            for (int i = lastIndexUsed; i < timestampsForRoute.size(); i++) {
                if (timestampsForRoute.get(i).getTimestamp() >= finalCurrEndTime) {
                    currLastIndex = i;
                    break;
                }
            }
            OptionalDouble averageDelay = timestampsForRoute.subList(lastIndexUsed, currLastIndex).stream()
                    .mapToDouble(RouteTimestamp::getAverageDelay).average();
            if (averageDelay.isPresent()) {
                Double timeInMinutes = averageDelay.getAsDouble() / 60; //convert to minutes
                currData.add(floor(timeInMinutes * 1000) / 1000);
            } else {
                currData.add(null);
            }
            //get ready for next iteration
            lastIndexUsed = currLastIndex;
        }
        return currData;
    }
}
