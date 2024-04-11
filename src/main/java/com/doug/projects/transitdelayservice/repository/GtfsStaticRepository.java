package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.AGENCY_TYPE_INDEX;

@Repository
@Slf4j
public class GtfsStaticRepository {
    private final DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    private final DynamoDbAsyncTable<GtfsStaticData> table;

    public GtfsStaticRepository(DynamoDbEnhancedAsyncClient enhancedAsyncClient) {
        this.enhancedAsyncClient = enhancedAsyncClient;
        this.table = enhancedAsyncClient.table("gtfsData", TableSchema.fromBean(GtfsStaticData.class));
        try (var waiter = DynamoDbWaiter.create()) {
            table.createTable();
            waiter.waitUntilTableExists(builder -> builder.tableName("gtfsData"));
        }
    }

    public void save(GtfsStaticData data) {
        table.putItem(data);
    }

    /**
     * Writes all of the items in data to table asynchronously.
     * <p>Note that large Lists (greater than 500 in size) will be throttled heavily.</p>
     *
     * @param data the data to save to DynamoDb
     */
    public void saveAll(List<GtfsStaticData> data) {
        if (data.isEmpty()) return;
        DynamoUtils
                .chunkList(data, 500) //limit 500 per batch write.
                .forEach(this::parallelSaveAll);
    }

    /**
     * Writes all items in data to table synchronously. Chunked to Dynamo's 25 item maximum.
     *
     * @param data the data to save
     */
    private void parallelSaveAll(List<GtfsStaticData> data) {
        CompletableFuture<?>[] result = DynamoUtils.chunkList(data, 25)
                .stream()
                .map(this::asyncBatchWrite)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(result)
                .join();
    }

    /**
     * Writes all items in list to DynamoDb asynchronously.
     *
     * @param chunkedList a list of size 25 or lower
     * @return a completableFuture which writes this data
     */
    private CompletableFuture<Void> asyncBatchWrite(List<GtfsStaticData> chunkedList) {
        return enhancedAsyncClient.batchWriteItem(b -> addBatchWrites(chunkedList, b))
                //if the write takes longer than 10 seconds, we get an exception that kills the whole process
                //we add a cutoff here that tries to prevent that exception.
                //if writing just 25 items takes over 10 seconds, the partition might be overloaded
                .completeOnTimeout(null, 9500, TimeUnit.MILLISECONDS)
                .thenAccept(r -> retryUnprocessed(chunkedList, r));
    }

    private void addBatchWrites(List<GtfsStaticData> chunkedList, BatchWriteItemEnhancedRequest.Builder b) {
        for (var item : chunkedList) {
            b.addWriteBatch(WriteBatch.builder(GtfsStaticData.class).mappedTableResource(table).addPutItem(item).build());
        }
    }

    @SneakyThrows(InterruptedException.class)
    private void retryUnprocessed(List<GtfsStaticData> data, BatchWriteResult r) {
        if (r == null) {
            log.error("Timeout writing to dynamoDB. Retrying...");
            Thread.sleep(5000);
            asyncBatchWrite(data).join();
            return;
        }
        if (!r.unprocessedPutItemsForTable(table).isEmpty()) {
            log.error("Unprocessed items: {}", r.unprocessedPutItemsForTable(table));
            asyncBatchWrite(r.unprocessedPutItemsForTable(table)).join();
        }
    }

    public CompletableFuture<List<GtfsStaticData>> findAllRoutes(String agencyId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(agencyId + ":" + GtfsStaticData.TYPE.ROUTE.getName()));
        QueryEnhancedRequest queryEnhancedRequest = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();
        SdkPublisher<Page<GtfsStaticData>> sdkPublisher = table.index(AGENCY_TYPE_INDEX)
                .query(queryEnhancedRequest);
        //RxJava stuff. Convert the list query to a list of routeName, get distinct, and return as list.
        return Flux.concat(sdkPublisher)
                .flatMapIterable(Page::items)
                .sort(Comparator.comparing(GtfsStaticData::getRouteSortOrder))
                .distinct()
                .collectList()
                .toFuture();
    }

    /**
     * Finds all route names for a particular agencyId.
     * Note that this can either be routeShortName or routeLongName, depending on which was specified in routes.txt file.
     *
     * @param agencyId the agencyId to search the DB for
     * @return the routeNames associated with that agency.
     */
    public CompletableFuture<List<String>> findAllRouteNames(String agencyId) {
        return this.findAllRoutes(agencyId).thenApply(l -> l.stream().map(GtfsStaticData::getRouteName).toList());
    }

    public Map<String, String> mapRouteIdsToRouteName(String agencyId, List<String> routeIds) {
        if (StringUtils.isBlank(agencyId) || CollectionUtils.isEmpty(routeIds)) return Collections.emptyMap();
        List<String> uniqueRouteIds = routeIds.stream().distinct().toList();
        return DynamoUtils.chunkList(uniqueRouteIds, 100).stream().flatMap(chunkList -> {
            BatchGetItemEnhancedRequest enhancedRequest = BatchGetItemEnhancedRequest.builder().readBatches(generateReadBatches(agencyId, chunkList, GtfsStaticData.TYPE.ROUTE.getName())).build();
            return Flux.from(enhancedAsyncClient.batchGetItem(enhancedRequest))
                    .flatMapIterable(p -> p.resultsForTable(table))
                    .filter(Objects::nonNull)
                    .toStream();
        }).collect(Collectors.toMap(GtfsStaticData::getId, GtfsStaticData::getRouteName));
    }

    public Map<String, String> mapTripIdsToRouteName(String agencyId, List<String> tripIds) {
        if (StringUtils.isBlank(agencyId) || CollectionUtils.isEmpty(tripIds)) return Collections.emptyMap();
        List<String> uniqueTripIds = tripIds.stream().distinct().toList();
        return DynamoUtils.chunkList(uniqueTripIds, 100).stream().flatMap(chunkList -> {
            BatchGetItemEnhancedRequest enhancedRequest = BatchGetItemEnhancedRequest.builder().readBatches(generateReadBatches(agencyId, chunkList, GtfsStaticData.TYPE.TRIP.getName())).build();
            return Flux.from(enhancedAsyncClient.batchGetItem(enhancedRequest))
                    .flatMapIterable(p -> p.resultsForTable(table))
                    .filter(Objects::nonNull)
                    .toStream();
        }).collect(Collectors.toMap(GtfsStaticData::getId, GtfsStaticData::getRouteName));
    }

    @NotNull
    private List<ReadBatch> generateReadBatches(String agencyId, List<String> chunk, String name) {
        return chunk.stream().map(routeId -> ReadBatch.builder(GtfsStaticData.class).addGetItem(Key.builder().partitionValue(routeId).sortValue(agencyId + ":" + name).build()).mappedTableResource(table).build()).toList();
    }

    public CompletableFuture<Map<String, String>> getRouteNameToColorMap(String agencyId) {
        return this.findAllRoutes(agencyId).thenApply(l ->
                l.stream().collect(
                        Collectors.toMap(GtfsStaticData::getRouteName, GtfsStaticData::getRouteColor)));
    }

    public CompletableFuture<Map<String, Integer>> getRouteNameToSortOrderMap(String agencyId) {
        return this.findAllRoutes(agencyId).thenApply(l ->
                l.stream().collect(
                        Collectors.toMap(GtfsStaticData::getRouteName, GtfsStaticData::getRouteSortOrder)));
    }
}
