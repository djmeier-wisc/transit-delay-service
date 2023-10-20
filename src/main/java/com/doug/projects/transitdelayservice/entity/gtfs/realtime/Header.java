package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class Header {
    private String gtfs_realtime_version;
    private Integer incrementality;
    private Integer timestamp;
}
