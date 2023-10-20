package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

import java.util.ArrayList;
@Data
public class RealtimeTransitResponse {
    private Header header;
    private ArrayList<Entity> entity;
}
