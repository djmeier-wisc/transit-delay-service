package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Repository
@Slf4j
public class AgencyRouteTimestampRepository {
    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncTable<AgencyRouteTimestamp> table;

    public AgencyRouteTimestampRepository(DynamoDbEnhancedAsyncClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table("routeTimestamp", TableSchema.fromBean(AgencyRouteTimestamp.class));
    }

    public void save(AgencyRouteTimestamp agencyRouteTimestamp) {
        table.putItem(agencyRouteTimestamp);
    }

    public void saveAll(List<AgencyRouteTimestamp> agencyRouteTimestampList) {
        if (agencyRouteTimestampList.isEmpty()) return;
        DynamoUtils.chunkList(agencyRouteTimestampList, 25)
                .stream().map(chunkList -> {
                    try {
                        return enhancedClient.batchWriteItem(b ->
                                chunkList.forEach(agencyRouteTimestamp -> {
                                    b.addWriteBatch(
                                            WriteBatch.builder(AgencyRouteTimestamp.class)
                                                    .mappedTableResource(table)
                                                    .addPutItem(agencyRouteTimestamp)
                                                    .build());
                                })
                        ).thenAcceptAsync();
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to write {}", chunkList, e);
                    }
                    return CompletableFuture.completedFuture(null);
                }).reduce(Comp);
    }
}
