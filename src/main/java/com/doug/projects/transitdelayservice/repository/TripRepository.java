package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.Trip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class TripRepository {
    private final DynamoDbTable<Trip> table;

    public List<Integer> getRouteIdsFor(List<Integer> tripIds) {
        return tripIds.parallelStream().flatMap(id -> Stream.ofNullable(getRouteIdFor(id).orElse(null))).toList();
    }

    public Optional<Integer> getRouteIdFor(Integer tripId) {
        Trip trip = table.getItem(Key.builder().partitionValue(tripId).build());
        if (trip == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(trip.getRoute_id());
    }

    public List<Integer> getTripIdsFor(List<Integer> routeIds) {
        return routeIds.parallelStream().flatMap(routeId -> table.index("route-short-name-index")
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(routeId).build())).stream()
                .flatMap(tripPage -> tripPage.items().stream().map(Trip::getTrip_id))).collect(Collectors.toList());
    }
}