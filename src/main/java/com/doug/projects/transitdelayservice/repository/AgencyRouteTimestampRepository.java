package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
        DynamoUtils.chunkList(data, 25)
                .stream()
                .map(this::asyncBatchWrite)
                .reduce(CompletableFuture::allOf)
                .orElse(CompletableFuture.failedFuture(new RuntimeException("Failed to write all items to dynamoDB")))
                .join();
    }

    /**
     * Writes all items in list to DynamoDb asynchronously.
     *
     * @param chunkedList
     * @return
     */
    private CompletableFuture<Void> asyncBatchWrite(List<AgencyRouteTimestamp> chunkedList) {
        return asyncEnhancedClient.batchWriteItem(b -> addBatchWrites(chunkedList, b))
                //if the write takes longer than 10 seconds, we get an exception that kills the whole process
                //we add a cutoff here that tries to prevent that exception.
                //if writing just 25 items takes over 10 seconds, the partition might be overloaded
                .completeOnTimeout(null, 9500, TimeUnit.MILLISECONDS)
                .thenAccept(r -> retryUnprocessed(chunkedList, r));
    }

    private void addBatchWrites(List<AgencyRouteTimestamp> chunkedList, BatchWriteItemEnhancedRequest.Builder b) {
        for (var item : chunkedList) {
            b.addWriteBatch(WriteBatch.builder(AgencyRouteTimestamp.class)
                    .mappedTableResource(table)
                    .addPutItem(item)
                    .build());
        }
    }

    @SneakyThrows(InterruptedException.class)
    private void retryUnprocessed(List<AgencyRouteTimestamp> data, BatchWriteResult r) {
        if (r == null) {
            log.error("Timeout writing to dynamoDB. Retrying...");
            Thread.sleep(5000);
            asyncBatchWrite(data).join();
            return;
        }
        if (!r.unprocessedPutItemsForTable(table)
                .isEmpty()) {
            log.error("Unprocessed items: {}", r.unprocessedPutItemsForTable(table));
            asyncBatchWrite(r.unprocessedPutItemsForTable(table)).join();
        }
    }
}
