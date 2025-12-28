package com.doug.projects.transitdelayservice.entity;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
    ACTIVE("ACT"),
    UNAUTHORIZED("UNAUTHORIZED"), //token missing / 401 / 403 status
    UNAVAILABLE("UNAVAILABLE"), //generic connection error
    OUTDATED("OUTDATED"), //used when we are unable to find the routeId sent in a realtime response
    TIMEOUT("TIMEOUT"), //used when we are not able to download the feed in a certain amount of time
    DELETED("DEL");

    private final String name;
    Status(String s) {
        name = s;
    }

    @JsonValue
    public String toString() {
        return this.name;
    }
}
