package com.doug.projects.transitdelayservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphOptions {
    //the left and upper bound of the graph respectively
    private Long startTime, endTime;
    //the 'width' or number of units to generate a graph
    private Integer units;
    //whether to use the colors gathered from static gtfs data
    private Boolean useColor;
    //the routes to query the db for
    private List<String> routes;
    private String feedId;
    private Integer lowerOnTimeThreshold;
    private Integer upperOnTimeThreshold;
}
