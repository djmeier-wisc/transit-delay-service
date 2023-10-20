package com.doug.projects.transitdelayservice.entity.transit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Itinerary {
    @JsonProperty("branch_code")
    private String branchCode;
    @JsonProperty("direction_headsign")
    private String directionHeadsign;
    @JsonProperty("direction_id")
    private Integer directionId;
    @JsonProperty("headsign")
    private String headsign;
    @JsonProperty("merged_headsign")
    private String mergedHeadsign;
    @JsonProperty("schedule_items")
    private List<ScheduleItem> scheduleItems;
}
