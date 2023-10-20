package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

import java.util.ArrayList;
@Data
public class TripUpdate {
    private Trip trip;
    private ArrayList<StopTimeUpdate> stop_time_update;
    private Vehicle vehicle;
    private Integer timestamp;
    private Integer delay;
}
