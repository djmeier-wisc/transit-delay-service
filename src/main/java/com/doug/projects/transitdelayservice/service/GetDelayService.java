package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.LineGraphUtil.getColumnLabels;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDelayService {
    private final RouteTimestampRepository repository;
    private final RouteMapperService routeMapperService;
    private final LineGraphUtil lineGraphUtil;


    /**
     * Gets the average delay of <code>routes</code> between <code>startTime</code> and <code>endTime</code>, returning
     * a list of size <code>units</code>. Returns <code>LineGraphResponse</code>, to be used with Chart.JS formatting.
     *
     * @param startTime the time to begin search by, in unix epoch seconds
     * @param endTime   the time to end search by, in unix epoch seconds
     * @param units     how 'wide' the graph will be
     * @param routes    the routes to search by. NOTE: if null is supplied, all routes will be searched.
     * @return graph a Chart.js LineGraph data object, <code>units</code>. Note that labels will eithe be the date (if
     * the distance measured by each unit is a day or longer), or date & time (if the distance measured by each unit is
     * less than a day). Delay over the period is averaged to the thousandths decimal place.
     * @throws IllegalArgumentException if startTime is >= endTime
     */
    public LineGraphDataResponse getAverageDelay(Long startTime, Long endTime, Integer units, List<String> routes) throws IllegalArgumentException {
        log.info("Getting {} units of delays between {} and {} for routes {}", units, startTime, endTime, routes);
        //validations, if not set default
        return genericLineGraphConverter(startTime, endTime, units, routes,
                RouteTimestampUtil::getAverageDelayDataForRouteInMinutes);
    }


    /**
     * Gets the MAX delay of <code>routes</code> between <code>startTime</code> and <code>endTime</code>, returning a
     * list of <code>LineGraphData</code> of size <code>units</code>.
     *
     * @param startTime the time to begin search by, in unix epoch seconds
     * @param endTime   the time to end search by, in unix epoch seconds
     * @param units     how 'wide' the graph will be
     * @param routes    the routes to search by. NOTE: if null is supplied, all routes will be searched.
     * @return graph a Chart.js LineGraph data object, <code>units</code>. Note that labels will eithe be the date (if
     * the distance measured by each unit is a day or longer), or date & time (if the distance measured by each unit is
     * less than a day). Delay over the period is averaged to the thousandths decimal place.
     * @throws IllegalArgumentException if startTime is >= endTime
     */
    public LineGraphDataResponse getMaxDelayFor(Long startTime, Long endTime, Integer units, List<String> routes) throws IllegalArgumentException {
        //validations, if not set default
        return genericLineGraphConverter(startTime, endTime, units, routes,
                RouteTimestampUtil::getMaxDelayForRouteInMinutes);
    }

    /**
     * Generic wrapper function that iterates over a collection of routeTimeStamps gathered from the DB.
     *
     * @param startTime the startTime to search the db for, in unix time. If null, use midnight 6 days ago
     * @param endTime   the endTime to search the db for, in unix time. If null, use midnight tonight.
     * @param units     the number of columns in the graph. If null, use 7
     * @param routes    the routes to search for in the db. If null, use getAllFriendlyNames
     * @param converter the function run for after gathering all of the routeTimestamps.
     * @return a graph, beginning at startTime, ending at endTime, over the number of units
     */
    public LineGraphDataResponse genericLineGraphConverter(Long startTime, Long endTime, Integer units,
                                                           List<String> routes, RouteTimestampConverter converter) {
        final Long finalStartTime = startTime == null ? TransitDateUtil.getMidnightSixDaysAgo() : startTime;
        final Long finalEndTime = endTime == null ? TransitDateUtil.getMidnightTonight() : endTime;
        final Integer finalUnits = units == null ? 7 : units;
        final List<String> finalRoutes =
                CollectionUtils.isEmpty(routes) ? routeMapperService.getAllFriendlyNames() : routes;

        if (finalStartTime >= finalEndTime)
            throw new IllegalArgumentException("startTime must be greater than endTime");

        LineGraphDataResponse response = new LineGraphDataResponse();

        response.setLabels(getColumnLabels(finalStartTime, finalEndTime, finalUnits));
        var routeTimestampsMap = repository.getRouteTimestampsMapBy(finalStartTime, finalEndTime, finalRoutes);
        List<LineGraphData> lineGraphDataList =
                routeTimestampsMap.entrySet().parallelStream().map(routeFriendlyName -> {
                    List<RouteTimestamp> timestampsForRoute = routeFriendlyName.getValue();
                    if (timestampsForRoute == null) {
                        timestampsForRoute = Collections.emptyList();
                    }
                    List<Double> currData = new ArrayList<>(finalUnits);
                    try {
                        double perUnitSecondLength = (double) (finalEndTime - finalStartTime) / finalUnits;
                        int lastIndexUsed = 0;
                        for (int currUnit = 0; currUnit < finalUnits; currUnit++) {
                            final long finalCurrEndTime =
                                    (long) (finalStartTime + (perUnitSecondLength * (currUnit + 1)));
                            int currLastIndex = timestampsForRoute.size();
                            for (int i = lastIndexUsed; i < timestampsForRoute.size(); i++) {
                                if (timestampsForRoute.get(i).getTimestamp() >= finalCurrEndTime) {
                                    currLastIndex = i;
                                    break;
                                }
                            }
                            Double converterResult =
                                    converter.convert(timestampsForRoute.subList(lastIndexUsed, currLastIndex));
                            currData.add(converterResult);
                            //get ready for next iteration
                            lastIndexUsed = currLastIndex;
                        }
            } catch (Exception e) {
                        log.error("Failed to create for friendlyName: {}", routeFriendlyName, e);
                    }
                    return lineGraphUtil.getLineGraphData(routeFriendlyName.getKey(), currData);
        }).collect(Collectors.toList());
        lineGraphUtil.sortByGTFSSortOrder(lineGraphDataList);
        response.setDatasets(lineGraphDataList);
        return response;
    }
}
