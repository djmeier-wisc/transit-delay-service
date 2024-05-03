package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@DynamoDbBean
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GtfsStaticData {
    public static final String AGENCY_TYPE_INDEX = "agencyType-index";
    //{agency_id}:{type}
    @Getter(onMethod = @__({@DynamoDbSortKey,
            @DynamoDbSecondaryPartitionKey(indexNames = {AGENCY_TYPE_INDEX})}))
    private String agencyType;
    //route_id, trip_id, trip_id:stop_sequence (for stopTime), stop_id
    //although unintuitive, this is the PK to prevent hot partitions
    //read indexes will be needed to query this data
    @Getter(onMethod = @__({@DynamoDbPartitionKey}))
    private String id;
    //used for trips and routes
    private String routeName;
    private String routeColor;
    private Integer routeSortOrder;
    private String departureTime;
    private String arrivalTime;
    private String stopName;
    private String stopId;
    private Double stopLat;
    private Double stopLon;

    public void setAgencyType(String agencyId, TYPE type) {
        this.agencyType = agencyId + ":" + type.getName();
    }

    public @Nullable TYPE getType() {
        if (agencyType == null) return null;
        String[] split = agencyType.split(":");
        if (split.length <= 1) {
            return null;
        }
        for (TYPE value : TYPE.values()) {
            if (value.getName()
                    .equals(split[1])) {
                return value;
            }
        }
        return null;
    }

    public void setId(String tripId, Integer stopSequence) {
        this.id = tripId + ":" + stopSequence;
    }

    public String getTripId() {
        return switch (getType()) {
            case TRIP -> id;
            case STOPTIME -> StringUtils.substringBefore(id, ":");
            default -> null;
        };
    }

    public String getStopSequence() {
        if (getType() != TYPE.STOPTIME) {
            return null;
        }
        return StringUtils.substringAfter(id, ":");
    }

    /**
     * A list of GTFS file types. This list is not comprehensive, but represents what we actually care about reading.
     * Shape is commented out, since it isn't needed yet. It might be needed in the future for map display related
     * things. ORDER MATTERS! the order these are placed in is the order they are read in GtfsStaticParserService,
     * hence AGENCY is first to get the TZ for stopTimes.
     */
    @Getter
    public enum TYPE {
        AGENCY("AGENCY", "agency.csv"), ROUTE("ROUTE", "routes.csv"), TRIP("TRIP", "trips.csv"), STOP("STOP", "stops.csv"), STOPTIME("STOPTIME",
                "stop_times.csv");
        private final String name;
        private final String fileName;

        TYPE(String name, String fileName) {
            this.name = name;
            this.fileName = fileName;
        }
    }
}
