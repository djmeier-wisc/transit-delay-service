package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

@NoArgsConstructor
@Getter
@Setter
@Builder
@AllArgsConstructor
public class BusState {
    private Integer delay;
    private String closestStopId;
    private String tripId;

    public static BusState fromString(String stringToParse) {
        String[] split = stringToParse.split("#");
        BusState busState = new BusState();
        busState.setDelay(split[0].equalsIgnoreCase("null") ? null : toInt(split[0]));
        busState.setClosestStopId(split[1].equalsIgnoreCase("null") ? null : split[1]);
        busState.setTripId(split[2].equalsIgnoreCase("null") ? null : split[2]);
        return busState;
    }

    @Override
    public String toString() {
        return String.format("%d#%s#%s", getDelay(), getClosestStopId(), getTripId());
    }
}
