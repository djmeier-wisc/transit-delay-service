package com.doug.projects.transitdelayservice.entity.transit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stop {
    private int stop_id;
    private String stop_code;
    private String stop_name;
    private String stop_desc;
    private double stop_lat;
    private double stop_lon;
    private String agency_id;
    private String jurisdiction_id;
    private int location_type;
    private String parent_station;
    private int relative_position;
    private int cardinal_direction;
    private String wheelchair_boarding;
    private String primary_street;
    private String address_range;
    private String cross_location;
}
