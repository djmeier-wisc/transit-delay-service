package com.doug.projects.transitdelayservice.entity.transit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RouteDeparture {
    @JsonProperty("global_route_id")
    private String globalRouteId;
    @JsonProperty("real_time_route_id")
    private String realTimeRouteId;
    @JsonProperty("route_color")
    private String routeColor;
    @JsonProperty("route_short_name")
    private String routeShortName;
    @JsonProperty("branch_code")
    private String branchCode;
    @JsonProperty("itineraries")
    private List<Itinerary> itineraries;
}
