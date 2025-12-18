package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgencyRouteTimestamp {
    /**
     * The route
     */
    @Getter(AccessLevel.NONE)
    private String agencyRoute;
    /**
     * The timestamp, gathered from GTFS realtime feed. DO NOT USE SYSTEM TIMESTAMP to avoid duplication
     */
    private Long timestamp;
    /**
     * The list of routes, in the format of <code>BusStates</code> toString method. delay%closestStopId%tripId
     */
    private List<String> busStatesList;

    public String getRouteName() {
        return agencyRoute.split(":")[1];
    }

    public static String createKey(String agencyId, String routeName) {
        return agencyId + ":" + routeName;
    }

    public void setAgencyRoute(String agencyId, String routeName) {
        if (StringUtils.contains(agencyId, ":") || StringUtils.contains(routeName, ":")) {
            return;
        }
        agencyRoute = createKey(agencyId, routeName);
    }

    /**
     * Get a copy of the list of bus states. Changes to this list are not propagated, use setBusStates()
     *
     * @return a modifiable list copy of busStatesLIst
     */
    public List<BusState> getBusStatesCopyList() {
        return busStatesList.stream().map(BusState::fromString).collect(Collectors.toList());
    }

    public void setBusStates(List<BusState> busStates) {
        busStatesList = busStates.stream().map(BusState::toString).collect(Collectors.toList());
    }
}
