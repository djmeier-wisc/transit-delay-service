package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StopTime {
    private Integer trip_id;
    private Integer stop_sequence;
    private Integer stop_id;
    private String pickup_type;
    private String drop_off_type;
    private String arrival_time;
    private String departure_time;
    private String timepoint;
    private String stop_headsign;
    private Double shape_dist_traveled;

    @DynamoDbPartitionKey
    public Integer getStop_id() {
        return stop_id;
    }

    @DynamoDbSortKey
    public Integer getTrip_id() {
        return trip_id;
    }
}
