package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.Trip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
@Slf4j
public class TripRepository {
    private final DynamoDbTable<Trip> table;

    public List<Integer> getRouteIdsFor(List<Integer> tripIds) {
        return tripIds.parallelStream()
                .flatMap(id -> Stream.ofNullable(getRouteIdFor(id).orElse(null)))
                .toList();
    }

    public Optional<Integer> getRouteIdFor(Integer tripId) {
        try {
            return table.query(QueryConditional.keyEqualTo(Key.builder()
                            .partitionValue(tripId)
                            .build()))
                    .items()
                    .stream()
                    .map(Trip::getRoute_id)
                    .findAny();//this should only ever return 1

        } catch (Exception e) {
            log.error("Failed to get trip {}", tripId);
            return Optional.empty();
        }
    }

    public List<Integer> getTripIdsFor(List<Integer> routeIds) {
        var index = table.index("route_id-index");
        return routeIds.parallelStream()
                .flatMap(routeId -> index.query(QueryEnhancedRequest.builder()
                                .addAttributeToProject("trip_id")
                                .addAttributeToProject("route_id")
                                .queryConditional(QueryConditional.keyEqualTo(b -> b.partitionValue(routeId)))
                                .build())
                        .stream()
                        .flatMap(s -> s.items()
                                .stream())
                        .map(Trip::getTrip_id))
                .collect(Collectors.toList());
    }
}
