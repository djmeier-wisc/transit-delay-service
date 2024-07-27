package com.doug.projects.transitdelayservice.entity.gtfs.csv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShapeAttributes {
    @JsonProperty("shape_id")
    private String shapeId;
    @JsonProperty("shape_pt_lat")
    private double shapePtLat;
    @JsonProperty("shape_pt_lon")
    private double shapePtLon;
    @JsonProperty("shape_pt_sequence")
    private int shapePtSequence;
}
