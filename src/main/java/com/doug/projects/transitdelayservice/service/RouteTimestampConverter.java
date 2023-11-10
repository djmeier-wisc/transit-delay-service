package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;

import java.util.List;

public interface RouteTimestampConverter {
    List<Double> convert(List<RouteTimestamp> routeTimestampList, Long finalStartTime, Long finalEndTime,
                         Integer finalUnits);
}
