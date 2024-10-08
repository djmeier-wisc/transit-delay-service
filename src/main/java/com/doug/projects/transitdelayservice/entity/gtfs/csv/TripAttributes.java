package com.doug.projects.transitdelayservice.entity.gtfs.csv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripAttributes {
    @JsonProperty("trip_id")
    private String tripId;
    @JsonProperty("route_id")
    private String routeId;
    @JsonProperty("shape_id")
    private String shapeId;
}
