package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@DynamoDbBean
public class RouteTimeDelay {
    @Getter(AccessLevel.NONE)
    private String tripId;
    @Getter(AccessLevel.NONE)
    private Integer timestamp;
    private Integer delay;
    private String closestStopId;
    private Integer scheduledTime;
    private String route;

    @DynamoDbPartitionKey
    public String getTripId() {
        return tripId;
    }

    @DynamoDbSortKey
    public Integer getTimestamp() {
        return timestamp;
    }
}
