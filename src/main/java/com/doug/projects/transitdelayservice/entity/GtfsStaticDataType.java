package com.doug.projects.transitdelayservice.entity;

import lombok.Getter;

/**
 * A list of GTFS file types. This list is not comprehensive, but represents what we actually care about reading.
 * Shape is commented out, since it isn't needed yet. It might be needed in the future for map display related
 * things. ORDER MATTERS! the order these are placed in is the order they are read in GtfsStaticParserService,
 * hence AGENCY is first to get the TZ for stopTimes.
 */
@Getter
public enum GtfsStaticDataType {
    AGENCY("AGENCY", "agency.csv"),
    ROUTE("ROUTE", "routes.csv"),
    TRIP("TRIP", "trips.csv"),
    STOP("STOP", "stops.csv"),
    STOPTIME("STOPTIME", "stop_times.csv"),
    SHAPE("SHAPE", "shapes.csv");
    private final String name;
    private final String fileName;

    GtfsStaticDataType(String name, String fileName) {
        this.name = name;
        this.fileName = fileName;
    }
}
