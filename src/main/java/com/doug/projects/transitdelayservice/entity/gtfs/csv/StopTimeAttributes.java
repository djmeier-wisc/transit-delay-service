package com.doug.projects.transitdelayservice.entity.gtfs.csv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StopTimeAttributes {
    @JsonProperty("trip_id")
    private String tripId;
    @JsonProperty("stop_sequence")
    private Integer stopSequence;
    @JsonProperty("stop_id")
    private String stopId;
    @JsonProperty("departure_time")
    private String departureTime;
    @JsonProperty("arrival_time")
    private String arrivalTime;
}
