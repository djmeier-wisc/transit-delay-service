package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class Entity{
    private String id;
    private Boolean is_deleted;
    private TripUpdate trip_update;
    private Object vehicle;
    private Object alert;
}

