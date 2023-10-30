package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.LineGraphUtil.getColumnLabels;
import static com.doug.projects.transitdelayservice.util.RouteTimestampUtil.getAverageDelayDataForRouteInMinutes;
import static com.doug.projects.transitdelayservice.util.RouteTimestampUtil.getMaxDelayForRouteInMinutes;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDelayService {
    private final RouteTimestampRepository repository;
    private final RouteMapperService routesService;
    private final LineGraphUtil lineGraphUtil;


    /**
     * Gets the average delay of <code>route</code> between <code>startTime</code> and <code>endTime</code>, returning a
     * list of size <code>units</code>. Returns <code>LineGraphResponse</code>, to be used with Chart.JS formatting.
     *
     * @param startTime the time to begin search by, in unix epoch seconds
     * @param endTime   the time to end search by, in unix epoch seconds
     * @param units     how 'wide' the graph will be
     * @param route     the route to search by. NOTE: if null is supplied, all routes will be searched.
     * @return graph a Chart.js LineGraph data object, <code>units</code>. Note that labels will eithe be the date (if
     * the distance measured by each unit is a day or longer), or date & time (if the distance measured by each unit is
     * less than a day). Delay over the period is averaged to the thousandths decimal place.
     * @throws IllegalArgumentException if startTime is >= endTime
     */
    public LineGraphDataResponse getDelayFor(Long startTime, Long endTime, Integer units, String route) throws IllegalArgumentException {
        log.info("Getting {} units of delays between {} and {} for route {}", units, startTime, endTime, route);

        //validations, if not set default
        if (startTime == null)
            startTime = TransitDateUtil.getMidnightSixDaysAgo();

        if (endTime == null)
            endTime = TransitDateUtil.getMidnightTonight();

        if (units == null)
            units = 7;

        if (startTime >= endTime)
            throw new IllegalArgumentException("startTime must be less than endTime");


        double perUnitSecondLength = (double) (endTime - startTime) / units;

        List<LineGraphData> lineGraphDatas = new ArrayList<>(units);


        if (route != null) {
            List<RouteTimestamp> routeTimestamps = repository.getRouteTimestampsBy(startTime, endTime, route);
            List<Double> currData =
                    getAverageDelayDataForRouteInMinutes(startTime, units, perUnitSecondLength, routeTimestamps);
            lineGraphDatas.add(lineGraphUtil.getLineGraphData(route, currData));
        } else {
            Map<String, List<RouteTimestamp>> routeTimestamps = repository.getRouteTimestampsMapBy(startTime, endTime);
            for (String routeFriendlyName : routeTimestamps.keySet()) {
                List<RouteTimestamp> timestampsForRoute = routeTimestamps.get(routeFriendlyName);
                if (timestampsForRoute == null) {
                    continue;
                }
                List<Double> currData =
                        getAverageDelayDataForRouteInMinutes(startTime, units, perUnitSecondLength, timestampsForRoute);
                lineGraphDatas.add(lineGraphUtil.getLineGraphData(routeFriendlyName, currData));
            }
        }
        List<String> columnLabels = getColumnLabels(startTime, endTime, units);
        //sorts lineGraphDatas by friendlyName from RouteMapperService
        lineGraphUtil.sortByGTFSSortOrder(lineGraphDatas);
        return new LineGraphDataResponse(lineGraphDatas, columnLabels);
    }


    /**
     * Gets the MAX delay of <code>route</code> between <code>startTime</code> and <code>endTime</code>, returning a
     * list of <code>LineGraphData</code> of size <code>units</code>.
     *
     * @param startTime the time to begin search by, in unix epoch seconds
     * @param endTime   the time to end search by, in unix epoch seconds
     * @param units     how 'wide' the graph will be
     * @param route     the route to search by. NOTE: if null is supplied, all routes will be searched.
     * @return graph a Chart.js LineGraph data object, <code>units</code>. Note that labels will eithe be the date (if
     * the distance measured by each unit is a day or longer), or date & time (if the distance measured by each unit is
     * less than a day). Delay over the period is averaged to the thousandths decimal place.
     * @throws IllegalArgumentException if startTime is >= endTime
     */
    public LineGraphDataResponse getMaxDelayFor(Long startTime, Long endTime, Integer units, String route) throws IllegalArgumentException {
        //validations, if not set default
        if (startTime == null)
            startTime = TransitDateUtil.getMidnightSixDaysAgo();

        if (endTime == null)
            endTime = TransitDateUtil.getMidnightTonight();

        if (units == null)
            units = 7;

        if (startTime >= endTime)
            throw new IllegalArgumentException("startTime must be less than endTime");

        LineGraphDataResponse response = new LineGraphDataResponse();
        double perUnitSecondLength = (double) (endTime - startTime) / units;

        List<LineGraphData> lineGraphDataList = new ArrayList<>(routesService.getAllFriendlyNames().size());
        response.setLabels(getColumnLabels(startTime, endTime, units));
        if (route == null) {
            Long finalStartTime = startTime;
            Integer finalUnits = units;
            Long finalEndTime = endTime;
            var routeTimestampsMap = repository.getRouteTimestampsMapBy(startTime, endTime);
            lineGraphDataList = routeTimestampsMap.keySet().parallelStream().map(routeFriendlyName -> {
                List<RouteTimestamp> timestampsForRoute =
                        routeTimestampsMap.getOrDefault(routeFriendlyName, Collections.emptyList());
                List<Double> currData =
                        getMaxDelayForRouteInMinutes(finalStartTime, finalUnits, perUnitSecondLength,
                                timestampsForRoute);
                return lineGraphUtil.getLineGraphData(routeFriendlyName, currData);
            }).collect(Collectors.toList());
        } else {
            List<RouteTimestamp> timestampsForRoute = repository.getRouteTimestampsBy(startTime, endTime, route);
            List<Double> currData =
                    getMaxDelayForRouteInMinutes(startTime, units, perUnitSecondLength, timestampsForRoute);
            lineGraphDataList.add(lineGraphUtil.getLineGraphData(route, currData));
        }
        lineGraphUtil.sortByGTFSSortOrder(lineGraphDataList);
        response.setDatasets(lineGraphDataList);
        return response;
    }
}
