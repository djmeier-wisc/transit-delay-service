package com.doug.projects.transitdelayservice.entity.transit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DepartureTransitResponse {
    @JsonProperty("route_departures")
    List<RouteDeparture> routeDepartures;
}
