package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class StopTimeUpdate {
    private Integer stop_sequence;
    private Arrival arrival;
    private Departure departure;
    private Integer stop_id;
    private Integer schedule_relationship;
}
