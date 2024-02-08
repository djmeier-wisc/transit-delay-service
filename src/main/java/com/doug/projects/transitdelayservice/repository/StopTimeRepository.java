package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StopTimeRepository {
    private final DynamoDbEnhancedClient client;
    private final DynamoDbTable<StopTime> table;

    public Stream<StopTime> getStopTimes(@NonNull Integer tripId) {
        return table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(tripId).build())).items().stream();
    }

    public Stream<StopTime> getDeparturesFor(@NonNull List<Integer> tripIds) {
        var batchRead = ReadBatch.builder(StopTime.class).mappedTableResource(table);

        tripIds.forEach(tripId -> batchRead.addGetItem(Key.builder().partitionValue(tripId).build()));

        BatchGetItemEnhancedRequest batchGetItemEnhancedRequest =
                BatchGetItemEnhancedRequest.builder().readBatches(batchRead.build()).build();

        // Assuming "trip_id-index" is the name of your secondary index
        return client.batchGetItem(batchGetItemEnhancedRequest).resultsForTable(table).stream();
    }

    /**
     * Note that this stream is parallel. Order it if necessary.
     *
     * @param stopIds
     * @return
     */
    public Stream<StopTime> getStopTimes(Set<Integer> stopIds, List<Integer> tripIds) {
        return getDeparturesFor(tripIds).filter(stopTime -> stopIds.contains(stopTime.getStop_id()));
    }
}
