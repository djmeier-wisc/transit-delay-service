package com.doug.projects.transitdelayservice.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class LineGraphData {
    @JsonProperty("label")
    private String lineLabel;
    private List<Double> data;
    private Boolean fill = false;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String borderColor;
    private Double tension;
}
