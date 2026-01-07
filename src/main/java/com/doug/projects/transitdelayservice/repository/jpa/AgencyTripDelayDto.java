package com.doug.projects.transitdelayservice.repository.jpa;

public record AgencyTripDelayDto(String routeName, Long timestamp, Integer delaySeconds, String stopId, String tripId) {
}
