package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class Arrival{
    private int delay;
    private int time;
    private int uncertainty;
}