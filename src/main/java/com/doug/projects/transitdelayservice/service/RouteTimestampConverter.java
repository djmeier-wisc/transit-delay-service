package com.doug.projects.transitdelayservice.service;

import java.util.List;

public interface RouteTimestampConverter {
    Double convert(List<Agency> routeTimestampList);
}
