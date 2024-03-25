package com.doug.projects.transitdelayservice.entity;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import lombok.Builder;
import lombok.Data;

//Status of writing gtfs static data to disk.
@Data
@Builder
public class AgencyStaticStatus {
    private boolean success;
    private String message;
    private AgencyFeed feed;
}
