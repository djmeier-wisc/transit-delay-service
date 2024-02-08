package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

@DynamoDbBean
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RouteTimestamp {
    /**
     * The route
     */
    @Getter(AccessLevel.NONE)
    private String route;
    /**
     * The timestamp, gathered from GTFS realtime feed. DO NOT USE SYSTEM TIMESTAMP to avoid duplication
     */
    private Long timestamp;
    /**
     * The list of routes, in the format of <code>BusState</code> toString method. delay%closestStopId%tripId
     */
    private List<String> busStatesList;
    /**
     * The average difference from schedule of this route, across all active buses.
     */
    private Double averageDelay;

    @DynamoDbPartitionKey
    public String getRoute() {
        return route;
    }

    @DynamoDbSortKey
    public Long getTimestamp() {
        return timestamp;
    }
}
