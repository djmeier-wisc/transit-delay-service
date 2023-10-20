package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class Trip {
    private String trip_id;
    private String start_time;
    private String start_date;
    private Integer schedule_relationship;
    private String route_id;
    private Integer direction_id;
}
