package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;

import java.util.List;

public interface RouteTimestampConverter {
    Double convert(List<AgencyRouteTimestamp> routeTimestampList);
}
