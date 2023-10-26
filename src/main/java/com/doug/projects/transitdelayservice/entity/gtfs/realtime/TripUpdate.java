package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TripUpdate {
    private Trip trip;
    private List<StopTimeUpdate> stop_time_update;
    private Vehicle vehicle;
    private Integer timestamp;
    private Integer delay;
}
