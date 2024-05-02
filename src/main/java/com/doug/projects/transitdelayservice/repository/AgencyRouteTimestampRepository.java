package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp.createKey;
import static java.util.Collections.emptyList;

@Repository
@Slf4j
public class AgencyRouteTimestampRepository {
    private final DynamoDbEnhancedAsyncClient asyncEnhancedClient;
    private final DynamoDbAsyncTable<AgencyRouteTimestamp> table;

    public AgencyRouteTimestampRepository(DynamoDbEnhancedAsyncClient asyncEnhancedClient) {
        this.asyncEnhancedClient = asyncEnhancedClient;
        this.table = asyncEnhancedClient.table("routeTimestamp", TableSchema.fromBean(AgencyRouteTimestamp.class));
    }

    public void save(AgencyRouteTimestamp agencyRouteTimestamp) {
        table.putItem(agencyRouteTimestamp);
    }

    /**
     * Writes all items in data to table synchronously. Chunked to Dynamo's 25 item maximum.
     *
     * @param data the data to save
     */
    public void saveAll(List<AgencyRouteTimestamp> data) {
        List<AgencyRouteTimestamp> unfinishedWrites = new ArrayList<>(data);
        int numRetries = 0;
        do {
            CompletableFuture<List<AgencyRouteTimestamp>>[] result = DynamoUtils.chunkList(unfinishedWrites, 25)
                    .stream()
                    .map(this::asyncBatchWrite)
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(result)
                    .join();
            unfinishedWrites.clear();
            unfinishedWrites.addAll(Arrays.stream(result)
                    .map(CompletableFuture::join)
                    .flatMap(Collection::stream)
                    .toList());
        } while (!unfinishedWrites.isEmpty() && ++numRetries < 3);
    }

    /**
     * Writes all items in list to DynamoDb asynchronously. Returns any failures
     *
     * @param chunkedList
     * @return
     */
    private CompletableFuture<List<AgencyRouteTimestamp>> asyncBatchWrite(List<AgencyRouteTimestamp> chunkedList) {
        if (chunkedList.isEmpty()) return CompletableFuture.completedFuture(emptyList());
        return asyncEnhancedClient.batchWriteItem(b -> addBatchWrites(chunkedList, b)).thenApply(b -> b.unprocessedPutItemsForTable(table));
    }

    private void addBatchWrites(List<AgencyRouteTimestamp> chunkedList, BatchWriteItemEnhancedRequest.Builder b) {
        for (var item : chunkedList) {
            b.addWriteBatch(WriteBatch.builder(AgencyRouteTimestamp.class)
                    .mappedTableResource(table)
                    .addPutItem(item)
                    .build());
        }
    }

    private void retryUnprocessed(List<AgencyRouteTimestamp> data, BatchWriteResult r) {
        if (r == null) {
            log.error("Timeout writing to dynamoDB. Retrying...");
            asyncBatchWrite(data).join();
        } else if (!r.unprocessedPutItemsForTable(table)
                .isEmpty()) {
            log.error("Unprocessed items: {}", r.unprocessedPutItemsForTable(table));
            asyncBatchWrite(r.unprocessedPutItemsForTable(table)).join();
            log.info("Finished processing unprocessed items!");
        }
    }

    /**
     * Creates a collector that groups by agencyRoute and then sorts by timestamp.
     *
     * @return a map from routeName to
     */
    @NotNull
    private static Collector<AgencyRouteTimestamp, ?, Map<String, List<AgencyRouteTimestamp>>> groupByRouteNameAndSortByTimestamp() {
        return Collectors.groupingBy(AgencyRouteTimestamp::getRouteName,
                Collectors.collectingAndThen(Collectors.toList(), list -> {
                    list.sort(Comparator.comparing(AgencyRouteTimestamp::getTimestamp));
                    return list;
                }));
    }

    public Mono<Map<String, List<AgencyRouteTimestamp>>> getRouteTimestampsMapBy(long startTime, long endTime, List<String> routeNames, String feedId) {
        List<SdkPublisher<AgencyRouteTimestamp>> routeStream = routeNames.stream().map(routeName -> {
            Key lowerBound = Key.builder()
                    .partitionValue(createKey(feedId, routeName))
                    .sortValue(startTime)
                    .build();
            Key upperBound = Key.builder()
                    .partitionValue(createKey(feedId, routeName))
                    .sortValue(endTime)
                    .build();
            QueryConditional query = QueryConditional.sortBetween(lowerBound, upperBound);
            QueryEnhancedRequest request = QueryEnhancedRequest.builder().queryConditional(query).build();
            return table.query(request).items();
        }).toList();
        return Flux.merge(routeStream)
                .collect(groupByRouteNameAndSortByTimestamp());
    }

    public Flux<AgencyRouteTimestamp> getRouteTimestampsBy(long startTime, long endTime, String routeName, String feedId) {
        Key lowerBound = Key.builder()
                .partitionValue(createKey(feedId, routeName))
                .sortValue(startTime)
                .build();
        Key upperBound = Key.builder()
                .partitionValue(createKey(feedId, routeName))
                .sortValue(endTime)
                .build();
        QueryConditional query = QueryConditional.sortBetween(lowerBound, upperBound);
        QueryEnhancedRequest request = QueryEnhancedRequest.builder().queryConditional(query).build();
        return Flux.merge(table.query(request).items());
    }
}
