package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class BusState {
    private Integer delay;
    private String closestStopId;
    private String tripId;

    public static BusState fromString(String stringToParse) {
        String[] split = stringToParse.split("#");
        BusState busState = new BusState();
        busState.setDelay(Integer.parseInt(split[0]));
        busState.setClosestStopId(split[1]);
        busState.setTripId(split[2]);
        return busState;
    }

    @Override
    public String toString() {
        return String.format("%d#%s#%s", getDelay(), getClosestStopId(), getTripId());
    }
}
