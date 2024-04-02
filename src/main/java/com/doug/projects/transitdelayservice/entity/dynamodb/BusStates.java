package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class BusStates {
    private Integer delay;
    private String closestStopId;
    private Integer tripId;

    @Override
    public String toString() {
        return String.format("%d#%s#%d", getDelay(), getClosestStopId(), getTripId());
    }
}
