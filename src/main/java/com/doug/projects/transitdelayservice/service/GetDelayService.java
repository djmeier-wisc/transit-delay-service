package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.GraphOptions;
import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.BusState;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.RouteTimestampUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.doug.projects.transitdelayservice.util.LineGraphUtil.getColumnLabels;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDelayService {
    private final AgencyRouteTimestampRepository repository;
    private final LineGraphUtil lineGraphUtil;

    private static double threeDigitPrecision(double initial) {
        BigDecimal bd = new BigDecimal(initial).setScale(3, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

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
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil.toAvg());
    }

    public Mono<LineGraphDataResponse> getMedianDelay(String feedId, GraphOptions graphOptions) throws IllegalArgumentException {
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil.toMedian());
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
        return genericLineGraphConverter(feedId, graphOptions, RouteTimestampUtil.toMax());
    }

    public Mono<LineGraphDataResponse> getPercentOnTimeFor(String feedId, GraphOptions graphOptions) {
        if (graphOptions.getLowerOnTimeThreshold() == null) {
            graphOptions.setLowerOnTimeThreshold(-5);
        }
        if (graphOptions.getUpperOnTimeThreshold() == null) {
            graphOptions.setUpperOnTimeThreshold(5);
        }
        return genericLineGraphConverter(feedId, graphOptions,
                RouteTimestampUtil.toPercentWithin(
                        graphOptions.getLowerOnTimeThreshold(),
                        graphOptions.getUpperOnTimeThreshold()
                )
        );
    }

    /**
     * Generic wrapper function that iterates over a collection of routeTimeStamps gathered from the DB.
     *
     * @param graphOptions the options used to create a graph
     * @param collector    collects the list of delays to a double, which will appear on the graph. more memory efficient than previously
     * @return a graph, beginning at startTime, ending at endTime, over the number of units
     */
    private Mono<LineGraphDataResponse> genericLineGraphConverter(String feedId, GraphOptions graphOptions, Collector<Double, ?, Optional<Double>> collector) {
        final long startTime = graphOptions.getStartTime() == null ? TransitDateUtil.getMidnightSixDaysAgo() : graphOptions.getStartTime();
        final long endTime = graphOptions.getEndTime() == null ? TransitDateUtil.getMidnightTonight() : graphOptions.getEndTime();
        final int units = graphOptions.getUnits() == null ? 7 : graphOptions.getUnits();
        final String finalFeedId = feedId == null ? "394" : feedId; //default to calling madison metro transit, the original feed to support legacy calls.
        final List<String> finalRoutes = CollectionUtils.isEmpty(graphOptions.getRoutes()) ? Collections.emptyList() : graphOptions.getRoutes();
        final boolean useGtfsColor = graphOptions.getUseColor() == null || graphOptions.getUseColor(); //default to false unless specified
        if (startTime >= endTime)
            return Mono.error(new IllegalArgumentException("StartTime must be less than endTime"));
        final long bucketSize = (endTime - startTime) / units;
        return repository.getRouteTimestampsBy(startTime, endTime, finalRoutes, finalFeedId)
                .groupBy(AgencyRouteTimestamp::getRouteName)
                .flatMap(routeNameGroup -> groupAndGetLineGraphData(collector, routeNameGroup, startTime, endTime, bucketSize))
                .collectList()
                .map(s -> getLineGraphDataResponse(s, finalFeedId, useGtfsColor, startTime, endTime, units, finalRoutes));
    }

    private @NotNull Mono<LineGraphData> groupAndGetLineGraphData(
            Collector<Double, ?, Optional<Double>> collector,
            GroupedFlux<String, AgencyRouteTimestamp> routeNameGroup,
            long startTime,
            long endTime,
            long bucketSize) {

        return routeNameGroup
                .collectList()
                .flatMap(routeTimestamps -> {
                    // Total number of expected buckets (inclusive of startTime and endTime)
                    long bucketCount = ((endTime - startTime) / bucketSize);

                    // Step 1: Group data into buckets based on timestamp
                    Map<Long, List<AgencyRouteTimestamp>> grouped = routeTimestamps.stream()
                            .collect(Collectors.groupingBy(data -> {
                                long bucketIndex = (data.getTimestamp() - startTime) / bucketSize;
                                return startTime + (bucketIndex * bucketSize);
                            }));

                    // Step 2: For each expected bucket time, get processed value or null
                    List<Mono<Optional<Double>>> bucketMonos = LongStream.range(0, bucketCount)
                            .mapToObj(i -> startTime + (i * bucketSize))
                            .map(bucketStart -> {
                                List<AgencyRouteTimestamp> bucketData = grouped.get(bucketStart);
                                if (bucketData == null || bucketData.isEmpty()) {
                                    return Mono.just(Optional.<Double>empty());
                                }

                                return Flux.fromIterable(bucketData)
                                        .flatMapIterable(AgencyRouteTimestamp::getBusStatesCopyList)
                                        .mapNotNull(BusState::getDelay)
                                        .map(delay -> delay / 60d)
                                        .collect(collector)
                                        .map(opt -> opt.map(GetDelayService::threeDigitPrecision));
                            })
                            .toList();

                    // Step 3: Assemble final result list with nulls where data is missing
                    return Flux.concat(bucketMonos)
                            .collectList()
                            .map(bucketValues -> lineGraphUtil.getLineGraphData(routeNameGroup.key(), bucketValues.stream().map(o -> o.orElse(null)).toList()));
                });
    }


    private @NotNull LineGraphDataResponse getLineGraphDataResponse(List<LineGraphData> lineGraphDataList, String finalFeedId, boolean useGtfsColor, long startTime, long endTime, int units, List<String> finalRoutes) {
        lineGraphUtil.sortByGTFSSortOrder(finalFeedId, lineGraphDataList);
        if (useGtfsColor) {
            lineGraphUtil.populateColor(finalFeedId, lineGraphDataList);
        }
        LineGraphDataResponse response = new LineGraphDataResponse();
        response.setDatasets(lineGraphDataList);
        response.setLabels(getColumnLabels(startTime, endTime, units));
        return response;
    }
}
