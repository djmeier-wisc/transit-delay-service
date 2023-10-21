package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

@DynamoDbBean
@Data
public class RouteTimestamp {
    @Getter(AccessLevel.NONE)
    public String route;
    public Integer timestamp;
    public List<String> busStatesList;
    public Double averageDelay;

    @DynamoDbPartitionKey
    public String getRoute() {
        return route;
    }

    @DynamoDbSortKey
    public Integer getTimestamp() {
        return timestamp;
    }
}
