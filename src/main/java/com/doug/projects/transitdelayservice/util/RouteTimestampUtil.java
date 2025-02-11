package com.doug.projects.transitdelayservice.util;

import java.util.*;
import java.util.stream.Collector;

public class RouteTimestampUtil {
    public static Collector<Double, DoubleSummaryStatistics, Optional<Double>> toAvg() {
        return Collector.of(
                DoubleSummaryStatistics::new,
                DoubleSummaryStatistics::accept,
                (left, right) -> {
                    left.combine(right);
                    return left;
                },
                stats -> stats.getCount() > 0 ? Optional.of(stats.getAverage()) : Optional.empty() // Finisher: gets max
        );
    }

    public static Collector<Double, List<Double>, Optional<Double>> toMedian() {
        return Collector.of(
                ArrayList::new,   // Supplier: creates a new list
                List::add,        // Accumulator: adds elements to the list
                (left, right) -> {
                    left.addAll(right);
                    return left;
                }, // Combiner: merges two lists
                list -> {
                    if (list.isEmpty()) return Optional.empty();
                    Collections.sort(list);
                    int size = list.size();
                    if (size % 2 == 1) {
                        return Optional.of(list.get(size / 2));
                    } else {
                        return Optional.of((list.get(size / 2 - 1) + list.get(size / 2)) / 2.0);
                    }
                }
        );
    }

    public static Collector<Double, DoubleSummaryStatistics, Optional<Double>> toMax() {
        return Collector.of(
                DoubleSummaryStatistics::new,
                DoubleSummaryStatistics::accept,
                (left, right) -> {
                    left.combine(right);
                    return left;
                },
                stats -> stats.getCount() > 0 ? Optional.of(stats.getMax()) : Optional.empty() // Finisher: gets max
        );
    }

    public static Collector<Double, ?, Optional<Double>> toPercentWithin(double min, double max) {
        return Collector.of(
                () -> new double[2], // creates an array {countWithin, totalCount}
                (acc, value) -> {
                    if (value >= min && value <= max) {
                        acc[0]++;
                    }
                    acc[1]++;
                },
                (left, right) -> {
                    left[0] += right[0];
                    left[1] += right[1];
                    return left;
                },
                acc -> acc[1] == 0 ? Optional.empty() : Optional.of((acc[0] / acc[1]) * 100)
        );
    }
}
