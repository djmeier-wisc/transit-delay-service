package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@DynamoDbBean
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
    @Getter(AccessLevel.NONE)
    private Long timestamp;
    /**
     * The list of routes, in the format of <code>BusStates</code> toString method. delay%closestStopId%tripId
     */
    private List<String> busStatesList;

    @DynamoDbPartitionKey
    public String getAgencyRoute() {
        return agencyRoute;
    }

    @DynamoDbSortKey
    public Long getTimestamp() {
        return timestamp;
    }

    public String getAgencyId() {
        return agencyRoute.split(":")[0];
    }

    public String getRouteName() {
        return agencyRoute.split(":")[1];
    }

    public boolean setAgencyRoute(String agencyId, String routeName) {
        if (StringUtils.contains(agencyId, ":") || StringUtils.contains(routeName, ":")) {
            return false;
        }
        agencyRoute = agencyId + ":" + routeName;
        return true;
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

    public int getMedianDelaySeconds() {
        List<BusState> busStates = getBusStatesCopyList();
        busStates.sort(Comparator.comparing(BusState::getDelay));
        return busStates.get(busStates.size() / 2).getDelay();
    }

    public double getPercentOnTime() {
        List<BusState> busStates = getBusStatesCopyList();
        return (double) busStates.stream().filter(bs -> bs.getDelay() <= 0).count() / busStates.size();
    }

    public int getMaxDelaySeconds() {
        List<BusState> busStates = getBusStatesCopyList();
        return busStates.stream().map(BusState::getDelay).max(Integer::compare).orElse(0);
    }
}
