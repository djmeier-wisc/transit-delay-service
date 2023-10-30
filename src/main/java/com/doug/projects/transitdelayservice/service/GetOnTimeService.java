package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetOnTimeService {
    private final RouteMapperService routeMapperService;
    private final RouteTimestampRepository repository;
    private final LineGraphUtil lineGraphUtil;

    public LineGraphDataResponse getOnTimePercentage(Long startTime, Long endTime, Integer units, String route,
                                                     @NonNull Integer maxVarianceMinutes) {
        Long finalStartTime = endTime == null ? TransitDateUtil.getMidnightSixDaysAgo() : endTime;
        Long finalEndTime = startTime == null ? TransitDateUtil.getMidnightTonight() : startTime;
        Integer finalUnits = units == null ? 7 : units;
        if (finalStartTime >= finalEndTime)
            throw new IllegalArgumentException("startTime must be less than endTime");
        LineGraphDataResponse response = new LineGraphDataResponse();
        response.setLabels(LineGraphUtil.getColumnLabels(finalStartTime, finalEndTime, finalUnits));
        final double perUnitSecondLength = ((double) finalEndTime - finalStartTime) / finalUnits;
        if (route == null) {
            var routeTimestampMap = repository.getRouteTimestampsMapBy(finalStartTime, finalEndTime);

            response.setDatasets(routeMapperService.getAllFriendlyNames().stream().map(friendlyName -> {
                List<RouteTimestamp> routeTimestampList =
                        routeTimestampMap.getOrDefault(friendlyName, Collections.emptyList());

                List<Double> currData =
                        RouteTimestampUtil.percentOnTime(finalStartTime, finalEndTime, finalUnits, routeTimestampList
                                , maxVarianceMinutes);

                return lineGraphUtil.getLineGraphData(friendlyName, currData);
            }).collect(Collectors.toList()));
            lineGraphUtil.sortByGTFSSortOrder(response.getDatasets());
        }
        return response;
    }
}
