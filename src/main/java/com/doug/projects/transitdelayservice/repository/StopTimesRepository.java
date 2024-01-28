package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StopTimesRepository {
    private final DynamoDbTable<StopTime> table;

    public Stream<StopTime> getStopTimes(Integer stopId, Optional<Integer> tripId) {
        if (tripId.isPresent()) {
            return table.query(QueryConditional.keyEqualTo(Key.builder().sortValue(tripId.get()).partitionValue(stopId)
                    .build())).items().stream();
        } else {
            return table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(stopId).build())).items()
                    .stream();
        }
    }

    /**
     * Note that this stream is parallel. Order it if necessary.
     *
     * @param stopIds
     * @return
     */
    public Stream<StopTime> getStopTimes(List<Integer> stopIds) {
        return stopIds.parallelStream().flatMap(id -> getStopTimes(id, Optional.empty()));
    }
}
