package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.DelayObjectRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Math.floor;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDelayService {
    private final DelayObjectRepository repository;
    private final RouteMapperService routesService;

    private static List<Double> getDelayDataForRoute(Long startTime, Integer units, double perUnitSecondLength, List<RouteTimestamp> timestampsForRoute) {
        List<Double> currData = new ArrayList<>(units);
        int lastIndexUsed = 0;
        for (int currUnit = 0; currUnit < units; currUnit++) {
            final long finalCurrEndTime = (long) (startTime + (perUnitSecondLength * (currUnit + 1)));
            int currLastIndex = timestampsForRoute.size();
            for (int i = lastIndexUsed; i < timestampsForRoute.size(); i++) {
                if (timestampsForRoute.get(i).getTimestamp() >= finalCurrEndTime) {
                    currLastIndex = i;
                    break;
                }
            }
            OptionalDouble averageDelay = timestampsForRoute.subList(lastIndexUsed, currLastIndex).stream().mapToDouble(RouteTimestamp::getAverageDelay).average();
            if (averageDelay.isPresent()) {
                currData.add(floor(averageDelay.getAsDouble()));
            } else {
                currData.add(null);
            }
            //get ready for next iteration
            lastIndexUsed = currLastIndex;
        }
        return currData;
    }

    private LineGraphData getLineGraphData(String routeFriendlyName, List<Double> currData) {
        LineGraphData lineGraphData = new LineGraphData();
        lineGraphData.setLineLabel(routeFriendlyName);
        lineGraphData.setTension(.3);
        lineGraphData.setData(currData);
        lineGraphData.setBorderColor(routesService.getColorFor(routeFriendlyName));
        return lineGraphData;
    }

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
    public LineGraphDataResponse getDelayFor(@Nullable Long startTime, @Nullable Long endTime, @Nullable Integer units, @Nullable String route) {
        log.info("Getting {} units of delays between {} and {} for route {}", units, startTime, endTime, route);
        if (startTime == null) {
            Calendar day = Calendar.getInstance();
            day.set(Calendar.MILLISECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.DATE, -7);
            startTime = day.toInstant().getEpochSecond();
        }
        if (endTime == null) {
            endTime = System.currentTimeMillis() / 1000;
        }
        if (units == null) {
            units = 7;
        }
        if (startTime >= endTime) {
            throw new IllegalArgumentException("Start time must be less than endTime");
        }
        double perUnitSecondLength = (double) (endTime - startTime) / units;

        List<LineGraphData> lineGraphDatas = new ArrayList<>(units);
        List<String> columnLabels = new ArrayList<>(units);

        if (route != null) {
            List<RouteTimestamp> routeTimestamps = repository.getRouteTimestampsBy(startTime, endTime, route);
            List<Double> currData = getDelayDataForRoute(startTime, units, perUnitSecondLength, routeTimestamps);
            lineGraphDatas.add(getLineGraphData(route, currData));
        } else {
            Map<String, List<RouteTimestamp>> routeTimestamps = repository.getRouteTimestampsBy(startTime, endTime);
            for (String routeFriendlyName : routesService.getAllFriendlyNames()) {
                List<RouteTimestamp> timestampsForRoute = routeTimestamps.get(routeFriendlyName);
                if (timestampsForRoute == null) {
                    continue;
                }
                //to describe the most rec

                List<Double> currData = getDelayDataForRoute(startTime, units, perUnitSecondLength, timestampsForRoute);
                lineGraphDatas.add(getLineGraphData(routeFriendlyName, currData));
            }
        }
        for (int currUnit = 0; currUnit < units; currUnit++) {
            final long finalCurrStartTime = (long) (startTime + (perUnitSecondLength * currUnit));
            final long dayInSeconds = 24 * 60 * 60; //the number of seconds in a day
            SimpleDateFormat dateFormat;
            if (perUnitSecondLength >= dayInSeconds) {// if unit length we are going for is a day, set label to date format
                dateFormat = new SimpleDateFormat("MM/dd/yy");
            } else {//if unit length is < a day, measure distance in HH:MM format
                dateFormat = new SimpleDateFormat("MMM/dd/yy HH:mm:ss aa");
            }
            columnLabels.add(dateFormat.format(new Date(finalCurrStartTime * 1000)));
        }

        return new LineGraphDataResponse(lineGraphDatas, columnLabels);
    }
}
