package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

import java.util.List;

@Data
public class RealtimeTransitResponse {
    private Header header;
    private List<Entity> entity;
}
