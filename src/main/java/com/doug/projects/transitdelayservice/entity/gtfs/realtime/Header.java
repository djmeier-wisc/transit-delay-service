package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Header {
    private String gtfs_realtime_version;
    private Integer incrementality;
    private Integer timestamp;
}
