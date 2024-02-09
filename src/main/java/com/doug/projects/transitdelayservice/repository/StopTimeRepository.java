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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StopTimeRepository {
    private final DynamoDbEnhancedClient client;
    private final DynamoDbTable<StopTime> table;

    /**
     * Group list into list of lists of max size 100
     *
     * @param tripIds the tripIds to split up.
     * @return a list of lists, witch each sublist contianing a maximum of 100 items. Note that stopId is the "Key" and
     * tripId is the "value". I used this just to store kv pairs so we can do a quick(er) batch get.
     */
    private static List<List<Map.Entry<Integer, Integer>>> partitionList(List<Integer> stopIds, List<Integer> tripIds) {
        List<Map.Entry<Integer, Integer>> entryList = new ArrayList<>(tripIds.size() * stopIds.size() * 2);
        for (Integer tripId : tripIds) {
            for (Integer stopId : stopIds) {
                entryList.add(Map.entry(stopId, tripId));
            }
        }
        //this is gross, but I could not figure out a better way to batch query by stopId and tripId together
        List<List<Map.Entry<Integer, Integer>>> result = new ArrayList<>(entryList.size() * 2 / 100);
        for (int i = 0; i < entryList.size(); i += 100) {
            //the min of the size & i + 100 can never be greater than size, so we avoid IndexOutOfBounds exception
            result.add(entryList.subList(i, Math.min(i + 100, entryList.size())));
        }
        return result;
    }

    public Stream<StopTime> getStopTimes(@NonNull Integer tripId) {
        return table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(tripId).build())).items().stream();
    }

    /**
     * Note that this stream is parallel. Order it if necessary.
     *
     * @param stopIds
     * @return
     */
    public Stream<StopTime> getStopTimes(List<Integer> stopIds, List<Integer> tripIds) {
        var parititonedList = partitionList(stopIds, tripIds);
        //we can only read a max of 100 items at a time from dynamo
        return parititonedList.parallelStream()
                .flatMap(pTripIds -> {
                    var batchRead = ReadBatch.builder(StopTime.class)
                            .mappedTableResource(table);
                    pTripIds.forEach(kv -> batchRead.addGetItem(b -> b.consistentRead(false)
                            //value is tripId, key is stopId
                            .key(Key.builder()
                                    .partitionValue(kv.getValue())
                                    .sortValue(kv.getKey())
                                    .build())));
                    BatchGetItemEnhancedRequest batchGetItemEnhancedRequest = BatchGetItemEnhancedRequest.builder()
                            .readBatches(batchRead.build())
                            .build();
                    return client.batchGetItem(batchGetItemEnhancedRequest)
                            .resultsForTable(table)
                            .stream();
                });
    }
}
