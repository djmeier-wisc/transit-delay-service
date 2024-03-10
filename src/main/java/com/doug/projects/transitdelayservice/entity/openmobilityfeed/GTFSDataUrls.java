package com.doug.projects.transitdelayservice.entity.openmobilityfeed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GTFSDataUrls {
    private String staticUrl;
    private String realtimeUrl;
    private String realtimeOpenMobilityId;
}
