package com.doug.projects.transitdelayservice.entity.gtfs.csv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutesAttributes {
    @JsonProperty("route_sort_order")
    private Integer routeSortOrder;
    @JsonProperty("route_id")
    private String routeId;
    @JsonProperty("route_short_name")
    private String routeShortName;
    @JsonProperty("route_color")
    private String routeColor;
    @JsonProperty("route_service_name")
    private String routeServiceName;
}
