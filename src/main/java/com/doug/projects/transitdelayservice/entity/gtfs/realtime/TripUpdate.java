package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUpdate {
    private Trip trip;
    private List<StopTimeUpdate> stop_time_update;
    private Vehicle vehicle;
    private Integer timestamp;
    private Integer delay;
}
