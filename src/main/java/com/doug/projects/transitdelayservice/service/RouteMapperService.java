package com.doug.projects.transitdelayservice.service;

import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@NoArgsConstructor
public class RouteMapperService {
    private static Map<Integer, String> routeIdToServiceNameMap;

    /**
     * This is a mess, but yml doesn't support maps by default. To be worked on to make this less static
     */
    @PostConstruct
    private void setRouteIdToServiceNameMap() {
        routeIdToServiceNameMap = new HashMap<>();
        routeIdToServiceNameMap.putAll(Map.of(10546, "A", 10547, "B", 10548, "C", 10549, "D", 10550, "E", 10551, "F", 10552
                , "G", 10553, "H", 10554, "J", 10555, "L"));
        routeIdToServiceNameMap.putAll(Map.of(10556, "O", 10557, "P", 10558, "R", 10559, "S", 10560, "W", 10536, "28", 10537, "38", 10538, "55", 10539, "65", 10540, "75"));
        routeIdToServiceNameMap.putAll(Map.of(10541, "80", 10542, "81", 10543, "82", 10544, "84", 10561, "60", 10562, "61", 10563, "62", 10564, "63", 10567, "64"));
    }
    public String getFriendlyName(Integer routeId) {
        return routeIdToServiceNameMap.getOrDefault(routeId, "UNKNOWN_ROUTE");
    }
}
