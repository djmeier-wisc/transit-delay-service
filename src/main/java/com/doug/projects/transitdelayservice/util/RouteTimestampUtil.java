package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RouteTimestampUtil {
    public static Double maxDelayInMinutes(Flux<AgencyRouteTimestamp> timestampsForRoute) {

        Optional<BusState> maxDelay = timestampsForRoute.flatMapIterable(AgencyRouteTimestamp::getBusStatesCopyList)
                .collect(Collectors.maxBy(Comparator.comparing(BusState::getDelay)))
                .block();
        if (maxDelay.isEmpty()) {
            return null;
        }
        return ((double) maxDelay.get()
                .getDelay()) / 60;
    }

    public static Double percentOnTime(Flux<AgencyRouteTimestamp> timestampsForRoute, Integer lowerMinutes,
                                       Integer upperMinutes) {
        Long filteredCount = timestampsForRoute.flatMapIterable(AgencyRouteTimestamp::getBusStatesCopyList)
                .map(BusState::getDelay)
                .filter(delay -> delay / 60 >= lowerMinutes && delay / 60 <= upperMinutes)
                .count()
                .block();
        Long totalCount = timestampsForRoute.count()
                .block();
        if (filteredCount == null || totalCount == null)
            return null;
        return (100. * filteredCount) / totalCount;

    }

    @NotNull
    private static IntStream getDelayStream(AgencyRouteTimestamp rt) {
        return rt.getBusStatesCopyList()
                .stream()
                .mapToInt(BusState::getDelay);
    }

    public static Double averageDelayMinutes(Flux<AgencyRouteTimestamp> routeTimestampList) {
        Double average = routeTimestampList.flatMap(r -> Flux.fromIterable(r.getBusStatesCopyList()))
                .filter(Objects::nonNull)
                .collect(Collectors.averagingInt(BusState::getDelay))
                .block();
        if (average == null)
            return null;
        return average / 60.;
    }
}
