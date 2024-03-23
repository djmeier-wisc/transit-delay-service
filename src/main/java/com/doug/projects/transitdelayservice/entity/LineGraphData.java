package com.doug.projects.transitdelayservice.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import reactor.core.publisher.Flux;

@Data
public class LineGraphData {
    @JsonProperty("label")
    private String lineLabel;
    private Flux<Double> data;
    private Boolean fill = false;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String borderColor;
    private Double tension;
}
