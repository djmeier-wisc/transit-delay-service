package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.GraphOptions;
import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.util.LineGraphUtil.getColumnLabels;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDelayService {
    private final AgencyRouteTimestampRepository repository;
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
    public Mono<LineGraphDataResponse> getAverageDelay(String feedId, GraphOptions graphOptions) throws IllegalArgumentException {
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil::averageDelayInMinutes);
    }

    public Mono<LineGraphDataResponse> getMedianDelay(String feedId, GraphOptions graphOptions) throws IllegalArgumentException {
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil::medianDelayInMinutes);
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
    public Mono<LineGraphDataResponse> getMaxDelayFor(String feedId, GraphOptions graphOptions) throws IllegalArgumentException {
        //validations, if not set default
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil::maxDelayInMinutes);
    }

    public Mono<LineGraphDataResponse> getPercentOnTimeFor(String feedId, GraphOptions graphOptions, Integer lower, Integer upper) {
        return genericLineGraphConverter(feedId, graphOptions, ((routeTimestampList) -> RouteTimestampUtil.percentOnTime(routeTimestampList, lower, upper)));
    }

    public Mono<LineGraphDataResponse> getPercentOnTimeFor(String feedId, GraphOptions graphOptions) {
        if (graphOptions.getLowerOnTimeThreshold() == null) {
            graphOptions.setLowerOnTimeThreshold(-5);
        }
        if (graphOptions.getUpperOnTimeThreshold() == null) {
            graphOptions.setUpperOnTimeThreshold(5);
        }
        return genericLineGraphConverter(feedId, graphOptions, ((routeTimestampList) ->
                RouteTimestampUtil.percentOnTime(routeTimestampList,
                        graphOptions.getLowerOnTimeThreshold(),
                        graphOptions.getUpperOnTimeThreshold())));
    }

    /**
     * Generic wrapper function that iterates over a collection of routeTimeStamps gathered from the DB.
     *
     * @param graphOptions the options used to create a graph
     * @param converter    Maps a list of routeTimestamp to a data point on a graph
     * @return a graph, beginning at startTime, ending at endTime, over the number of units
     */
    private Mono<LineGraphDataResponse> genericLineGraphConverter(String feedId, GraphOptions graphOptions, RouteTimestampConverter converter) {

        final long startTime = graphOptions.getStartTime() == null ? TransitDateUtil.getMidnightSixDaysAgo() : graphOptions.getStartTime();
        final long endTime = graphOptions.getEndTime() == null ? TransitDateUtil.getMidnightTonight() : graphOptions.getEndTime();
        final int units = graphOptions.getUnits() == null ? 7 : graphOptions.getUnits();
        final String finalFeedId = feedId == null ? "394" : feedId; //default to calling madison metro transit, the original feed to support legacy calls.
        final List<String> finalRoutes = CollectionUtils.isEmpty(graphOptions.getRoutes()) ? Collections.emptyList() : graphOptions.getRoutes();
        final boolean useGtfsColor = graphOptions.getUseColor() == null || graphOptions.getUseColor(); //default to false unless specified
        if (startTime >= endTime)
            return Mono.error(new IllegalArgumentException("StartTime must be less than endTime"));

        return repository.getRouteTimestampsMapBy(startTime, endTime, finalRoutes, feedId)
                .map(routeTimestampsMap -> getLineGraphData(converter, routeTimestampsMap, units, endTime, startTime))
                .map(lineGraphDataList -> getLineGraphDataResponse(lineGraphDataList, finalFeedId, useGtfsColor, startTime, endTime, units));
    }

    private @NotNull LineGraphDataResponse getLineGraphDataResponse(List<LineGraphData> lineGraphDataList, String finalFeedId, boolean useGtfsColor, long startTime, long endTime, int units) {
        lineGraphUtil.sortByGTFSSortOrder(finalFeedId, lineGraphDataList);
        if (useGtfsColor) {
            lineGraphUtil.populateColor(finalFeedId, lineGraphDataList);
        }
        LineGraphDataResponse response = new LineGraphDataResponse();
        response.setDatasets(lineGraphDataList);
        response.setLabels(getColumnLabels(startTime, endTime, units));
        return response;
    }

    private @NotNull List<LineGraphData> getLineGraphData(RouteTimestampConverter converter, Map<String, List<AgencyRouteTimestamp>> routeTimestampsMap, int units, long endTime, long startTime) {
        return routeTimestampsMap.entrySet().parallelStream().map(routeFriendlyName -> {
            List<AgencyRouteTimestamp> timestampsForRoute = routeFriendlyName.getValue();
            if (timestampsForRoute == null) {
                timestampsForRoute = Collections.emptyList();
            }
            List<Double> currData = new ArrayList<>(units * 2);
            double perUnitSecondLength = (double) (endTime - startTime) / units;
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
                Double converterResult = converter.convert(timestampsForRoute.subList(lastIndexUsed, currLastIndex));
                if (converterResult != null) {
                    BigDecimal bigDecimal = new BigDecimal(converterResult).setScale(3, RoundingMode.HALF_UP);
                    currData.add(bigDecimal.doubleValue());
                } else {
                    currData.add(null);
                }
                //get ready for next iteration
                lastIndexUsed = currLastIndex;
            }
            return lineGraphUtil.getLineGraphData(routeFriendlyName.getKey(), currData);
        }).collect(Collectors.toList());
    }
}
