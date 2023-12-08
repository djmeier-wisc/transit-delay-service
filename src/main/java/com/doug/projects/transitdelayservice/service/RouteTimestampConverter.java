package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;

import java.util.List;

public interface RouteTimestampConverter {
    Double convert(List<RouteTimestamp> routeTimestampList);
}
