package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import reactor.core.publisher.Flux;

public interface RouteTimestampConverter {
    Double convert(Flux<AgencyRouteTimestamp> routeTimestampList);
}
