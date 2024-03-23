package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class RouteTimestampUtil {
    public static Double maxDelayInMinutes(List<AgencyRouteTimestamp> timestampsForRoute) {

        OptionalInt maxDelay =
                timestampsForRoute.stream().flatMapToInt(RouteTimestampUtil::getDelayStream).max();
        if (maxDelay.isEmpty()) {
            return null;
        }
        return ((double) maxDelay.getAsInt()) / 60;
    }

    public static Double percentOnTime(List<AgencyRouteTimestamp> timestampsForRoute, Integer lowerMinutes, Integer upperMinutes) {

        int[] allBusStates = timestampsForRoute.stream()
                .flatMapToInt(RouteTimestampUtil::getDelayStream)
                .toArray();

        Double percentOnTime =
                ((double) Arrays.stream(allBusStates).filter(delay -> delay / 60 >= lowerMinutes && delay / 60 <= upperMinutes).count() /
                        allBusStates.length) * 100;
        if (allBusStates.length == 0) {
            return null;
        }
        return percentOnTime;

    }

    @NotNull
    private static IntStream getDelayStream(AgencyRouteTimestamp rt) {
        return rt.getBusStatesCopyList().stream().mapToInt(BusState::getDelay);
    }

    public static Double medianDelayInMinutes(List<AgencyRouteTimestamp> routeTimestampList) {
        if (CollectionUtils.isEmpty(routeTimestampList)) return null;
        int[] sortedDelayList = routeTimestampList.stream()
                .flatMapToInt(RouteTimestampUtil::getDelayStream)
                .filter(Objects::nonNull)
                .sorted() //sort by delay to get median
                .toArray();
        if (sortedDelayList.length == 0) {
            return null;
        }
        return (double) (sortedDelayList[sortedDelayList.length / 2] / 60);
    }
}
