package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
public class Trip {
    private Integer route_id;
    private String route_short_name;
    private String service_id;
    private Integer trip_id;
    private String trip_headsign;
    private Integer direction_id;
    private String trip_direction_name;
    private Integer block_id;
    private Integer shape_id;
    private String shape_code;
    private String trip_type;
    private Integer trip_sort;
    private Integer trip_short_name;
    private Integer block_transfer_id;
    private Integer wheelchair_accessible;
    private Integer bikes_allowed;

    @DynamoDbPartitionKey
    public Integer getTrip_id() {
        return trip_id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "route_id-index")
    @DynamoDbSortKey
    public Integer getRoute_id() {
        return route_id;
    }
}