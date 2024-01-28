package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.Builder;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@DynamoDbBean
public class Trip {
    private Integer route_id;
    private Integer route_short_name;
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

    @DynamoDbSortKey
    public Integer getTrip_id() {
        return trip_id;
    }

    // Constructors, toString(), hashCode(), equals(), etc., can be added as needed
}


