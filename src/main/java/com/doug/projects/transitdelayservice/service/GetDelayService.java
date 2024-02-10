package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.GraphOptions;
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
     * @param graphOptions the options used to create a graph
     * @return graph a Chart.js LineGraph data object, <code>units</code>. Note that labels will eithe be the date (if
     * the distance measured by each unit is a day or longer), or date & time (if the distance measured by each unit is
     * less than a day). Delay over the period is averaged to the thousandths decimal place.
     * @throws IllegalArgumentException if startTime is >= endTime
     */
    public LineGraphDataResponse getAverageDelay(GraphOptions graphOptions) throws IllegalArgumentException {
        return genericLineGraphConverter(graphOptions, RouteTimestampUtil::getAverageDelayDataForRouteInMinutes);
    }


    /**
     * Gets the MAX delay of <code>routes</code> between <code>startTime</code> and <code>endTime</code>, returning a
     * list of <code>LineGraphData</code> of size <code>units</code>.
     *
     * @param graphOptions the options used to create a graph
     * @return graph a Chart.js LineGraph data object, <code>units</code>. Note that labels will eithe be the date (if
     * the distance measured by each unit is a day or longer), or date & time (if the distance measured by each unit is
     * less than a day). Delay over the period is averaged to the thousandths decimal place.
     * @throws IllegalArgumentException if startTime is >= endTime
     */
    public LineGraphDataResponse getMaxDelayFor(GraphOptions graphOptions) throws IllegalArgumentException {
        //validations, if not set default
        return genericLineGraphConverter(graphOptions, RouteTimestampUtil::getMaxDelayForRouteInMinutes);
    }

    public LineGraphDataResponse getPercentOnTimeFor(GraphOptions graphOptions, Integer lower, Integer upper) {
        return genericLineGraphConverter(graphOptions, ((routeTimestampList) -> RouteTimestampUtil.percentOnTime(routeTimestampList, lower, upper)));
    }

    /**
     * Generic wrapper function that iterates over a collection of routeTimeStamps gathered from the DB.
     *
     * @param graphOptions the options used to create a graph
     * @param converter the function run for after gathering all routeTimestamps.
     * @return a graph, beginning at startTime, ending at endTime, over the number of units
     */
    private LineGraphDataResponse genericLineGraphConverter(GraphOptions graphOptions, RouteTimestampConverter converter) {
        final long finalStartTime = graphOptions.getStartTime() == null ? TransitDateUtil.getMidnightSixDaysAgo() : graphOptions.getStartTime();
        final long finalEndTime = graphOptions.getEndTime() == null ? TransitDateUtil.getMidnightTonight() : graphOptions.getEndTime();
        final int finalUnits = graphOptions.getUnits() == null ? 7 : graphOptions.getUnits();
        final List<String> finalRoutes = CollectionUtils.isEmpty(graphOptions.getRoutes()) ? routeMapperService.getAllFriendlyNames() : graphOptions.getRoutes();
        final boolean finalUseColor = graphOptions.getUseColor() == null || graphOptions.getUseColor(); //default to false unless specified
        if (finalStartTime >= finalEndTime)
            throw new IllegalArgumentException("startTime must be greater than endTime");


        var routeTimestampsMap = repository.getRouteTimestampsMapBy(finalStartTime, finalEndTime, finalRoutes);
        List<LineGraphData> lineGraphDataList = routeTimestampsMap.entrySet().parallelStream().map(routeFriendlyName -> {
            List<RouteTimestamp> timestampsForRoute = routeFriendlyName.getValue();
            if (timestampsForRoute == null) {
                timestampsForRoute = Collections.emptyList();
            }
            List<Double> currData = new ArrayList<>(finalUnits * 2);
            try {
                double perUnitSecondLength = (double) (finalEndTime - finalStartTime) / finalUnits;
                int lastIndexUsed = 0;
                for (int currUnit = 0; currUnit < finalUnits; currUnit++) {
                    final long finalCurrEndTime = (long) (finalStartTime + (perUnitSecondLength * (currUnit + 1)));
                    int currLastIndex = timestampsForRoute.size();
                    for (int i = lastIndexUsed; i < timestampsForRoute.size(); i++) {
                        if (timestampsForRoute.get(i).getTimestamp() >= finalCurrEndTime) {
                            currLastIndex = i;
                            break;
                        }
                    }
                    Double converterResult = converter.convert(timestampsForRoute.subList(lastIndexUsed, currLastIndex));
                    currData.add(converterResult);
                    //get ready for next iteration
                    lastIndexUsed = currLastIndex;
                }
            } catch (Exception e) {
                log.error("Failed to create for friendlyName: {}", routeFriendlyName, e);
            }
            return lineGraphUtil.getLineGraphData(routeFriendlyName.getKey(), currData, finalUseColor);
        }).collect(Collectors.toList());
        lineGraphUtil.sortByGTFSSortOrder(lineGraphDataList);

        LineGraphDataResponse response = new LineGraphDataResponse();
        response.setDatasets(lineGraphDataList);
        response.setLabels(getColumnLabels(finalStartTime, finalEndTime, finalUnits));
        return response;
    }
}
