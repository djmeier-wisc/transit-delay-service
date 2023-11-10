package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetOnTimeService {
    private final GetDelayService getDelayService;

    public LineGraphDataResponse getPercentOnTimeFor(Long startTime, Long endTime, Integer units, List<String> routes,
                                                     @NonNull Integer maxVarianceMinutes) {
        return getDelayService.genericLineGraphConverter(startTime, endTime, units, routes, ((routeTimestampList,
                                                                                              finalStartTime,
                                                                                              finalEndTime,
                                                                                              finalUnits) -> RouteTimestampUtil.percentOnTime(routeTimestampList, finalStartTime, finalEndTime, finalUnits, maxVarianceMinutes)));
    }
}
