package com.doug.projects.transitdelayservice.entity;

import lombok.Builder;
import lombok.Data;

//Status of writing gtfs static data to disk.
@Data
@Builder
public class AgencyGtfsWriteStatus {
    private boolean success;
    private String message;
    private String feedId;
}
