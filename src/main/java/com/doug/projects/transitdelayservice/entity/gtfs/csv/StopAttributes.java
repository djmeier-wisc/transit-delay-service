package com.doug.projects.transitdelayservice.entity.gtfs.csv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StopAttributes {
    @JsonProperty("stop_id")
    private String stopId;
    @JsonProperty("stop_name")
    private String stopName;
    @JsonProperty("stop_lat")
    private Double stopLat;
    @JsonProperty("stop_lon")
    private Double stopLon;
}
