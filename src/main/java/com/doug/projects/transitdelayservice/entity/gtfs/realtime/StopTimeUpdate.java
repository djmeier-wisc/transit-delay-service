package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class StopTimeUpdate {
    private Integer stop_sequence;
    private Arrival arrival;
    private Departure departure;
    private String stop_id;
    private Integer schedule_relationship;
}
