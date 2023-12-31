package com.doug.projects.transitdelayservice.entity.gtfs.csv;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"route_id", "service_id", "agency_id", "route_sort_order", "route_short_name", "route_long_name", "route_service_name", "route_desc", "checkin_duration", "route_type", "route_url", "route_color", "route_text_color", "bikes_allowed"})
public class RoutesAttributes {

    private Integer route_id, service_id, route_sort_order;
    private String agency_id, route_short_name, route_long_name, route_service_name, route_desc, checkin_duration, route_type, route_url, route_color, route_text_color, bikes_allowed;

}
