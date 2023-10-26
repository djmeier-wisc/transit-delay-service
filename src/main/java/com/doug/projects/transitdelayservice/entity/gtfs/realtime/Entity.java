package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Entity{
    private String id;
    private Boolean is_deleted;
    private TripUpdate trip_update;
    private Object vehicle;
    private Object alert;
}

