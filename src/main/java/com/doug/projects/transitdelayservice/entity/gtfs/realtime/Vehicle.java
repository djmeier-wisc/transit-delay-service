package com.doug.projects.transitdelayservice.entity.gtfs.realtime;

import lombok.Data;

@Data
public class Vehicle {
    private String id;
    private String label;
    private String license_plate;
}
