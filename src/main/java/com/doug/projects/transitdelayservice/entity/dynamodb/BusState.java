package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
//An individual bus along a route. Includes information like what its next stop is, how delayed it is, and more
public class BusState {
    private Integer delay;
    private Integer closestStopId;
    private Integer tripId;

    @Override
    public String toString() {
        return String.format("%d#%d#%d", getDelay(), getClosestStopId(), getTripId());
    }
}
