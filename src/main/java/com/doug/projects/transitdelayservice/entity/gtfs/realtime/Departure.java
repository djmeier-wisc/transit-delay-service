package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class Departure{
    private Integer delay;
    private Integer time;
    private Integer uncertainty;
}
