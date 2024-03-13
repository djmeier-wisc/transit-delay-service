package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@DynamoDbBean
public class GtfsStaticData {
    //{agency_id}:{type}
    @Getter(AccessLevel.NONE)
    private String agencyType;
    //trip_id, route_id, trip_id, trip_id:stop_sequence, stop_id
    @Getter(AccessLevel.NONE)
    private String id;
    private String routeName;
    private String routeColor;
    private String routeSortOrder;
    private String stopTimeStartTime;
    private String stopName;
    private String stopTimeStopId;


    public enum TYPE {
        AGENCY("AGENCY"),
        ROUTE("ROUTE"),
        TRIP("TRIP"),
        STOPTIME("STOPTIME"),
        STOP("STOP")
        ;
        private final String name;

        TYPE(String s) {
            name = s;
        }

        public String toString() {
            return this.name;
        }
    }
}
