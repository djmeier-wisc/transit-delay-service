package com.doug.projects.transitdelayservice.entity.transit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScheduleItem {
    @JsonProperty("departure_time")
    private Long departureTime;
    @JsonProperty("is_cancelled")
    private Boolean isCancelled;
    @JsonProperty("is_real_time")
    private Boolean isRealTime;
    @JsonProperty("rt_trip_id")
    private String routeTripId;
    /**
     * Note that this is sent as a
     */
    @JsonProperty("scheduled_departure_time")
    private Long scheduledDepartureTime;
    @JsonProperty("trip_search_key")
    private Integer scheduleItems;
}
