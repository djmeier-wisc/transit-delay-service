package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.GraphOptions;
import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;

import java.util.Comparator;
import java.util.List;

import static com.doug.projects.transitdelayservice.util.LineGraphUtil.getColumnLabels;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDelayService {
    private final AgencyRouteTimestampRepository repository;
    private final GtfsStaticRepository gtfsStaticRepository;
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
    public LineGraphDataResponse getAverageDelay(String feedId, GraphOptions graphOptions) throws IllegalArgumentException {
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil::averageDelayMinutes);
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
    public LineGraphDataResponse getMaxDelayFor(String feedId, GraphOptions graphOptions) throws IllegalArgumentException {
        //validations, if not set default
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil::maxDelayInMinutes);
    }

    public LineGraphDataResponse getPercentOnTimeFor(String feedId, GraphOptions graphOptions, Integer lower, Integer upper) {
        return genericLineGraphConverter(feedId, graphOptions, ((routeTimestampList) -> RouteTimestampUtil.percentOnTime(routeTimestampList, lower, upper)));
    }

    /**
     * Generic wrapper function that iterates over a collection of routeTimeStamps gathered from the DB.
     *
     * @param graphOptions the options used to create a graph
     * @param converter the function run for after gathering all routeTimestamps.
     * @return a graph, beginning at startTime, ending at endTime, over the number of units
     */
    private LineGraphDataResponse genericLineGraphConverter(String feedId, GraphOptions graphOptions, RouteTimestampConverter converter) {
        final long finalStartTime = graphOptions.getStartTime() == null ? TransitDateUtil.getMidnightSixDaysAgo() : graphOptions.getStartTime();
        final long finalEndTime = graphOptions.getEndTime() == null ? TransitDateUtil.getMidnightTonight() : graphOptions.getEndTime();
        final int finalUnits = graphOptions.getUnits() == null ? 7 : graphOptions.getUnits();
        final String finalFeedId = feedId == null ? "394" : feedId; //default to calling metro transit
        final List<String> finalRoutes = CollectionUtils.isEmpty(graphOptions.getRoutes()) ? gtfsStaticRepository.findAllRouteNames(feedId) : graphOptions.getRoutes();
        final boolean finalUseColor = graphOptions.getUseColor() == null || graphOptions.getUseColor(); //default to false unless specified
        if (finalStartTime >= finalEndTime)
            throw new IllegalArgumentException("startTime must be greater than endTime");


        var routeTimestampsMap = repository.getRouteTimestampsMapBy(finalStartTime, finalEndTime, finalRoutes, feedId);
        Flux<LineGraphData> lineGraphDataList = routeTimestampsMap.map(routeFriendlyName -> {
            Flux<AgencyRouteTimestamp> timestampsForRoute = routeFriendlyName;
            Flux<Double> currData = timestampsForRoute.groupBy(t -> (t.getTimestamp() - finalStartTime) /
                            (finalEndTime - finalStartTime))
                    .sort(Comparator.comparing(GroupedFlux::key))
                    .map(converter::convert);
            return lineGraphUtil.getLineGraphData(feedId, routeFriendlyName.key(), currData,
                    finalUseColor);
        });
        lineGraphUtil.sortByGTFSSortOrder(finalFeedId, lineGraphDataList);

        LineGraphDataResponse response = new LineGraphDataResponse();
        response.setDatasets(lineGraphDataList);
        response.setLabels(getColumnLabels(finalStartTime, finalEndTime, finalUnits));
        return response;
    }
}
