package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StopTimesRepository {
    private final DynamoDbTable<StopTime> table;

    public Stream<StopTime> getStopTimes(@NonNull Integer tripId, @Nullable Integer stopId) {
        if (stopId != null) {
            return table.query(QueryConditional.keyEqualTo(Key.builder().sortValue(stopId).partitionValue(tripId)
                    .build())).items().stream();
        } else {
            return table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(tripId).build())).items()
                    .stream();
        }
    }

    /**
     * Note that this stream is parallel. Order it if necessary.
     *
     * @param stopIds
     * @return
     */
    public Stream<StopTime> getStopTimes(Set<Integer> stopIds, List<Integer> tripIds) {
        return tripIds.parallelStream().flatMap(tripId -> getStopTimes(tripId, null))
                .filter(stopTime -> stopIds.contains(stopTime.getStop_id()));
    }
}
