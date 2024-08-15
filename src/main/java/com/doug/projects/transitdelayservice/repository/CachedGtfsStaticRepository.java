package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

@Repository
public class CachedGtfsStaticRepository {
    private final DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    private final DynamoDbAsyncTable<GtfsStaticData> table;

    public CachedGtfsStaticRepository(DynamoDbEnhancedAsyncClient enhancedAsyncClient) {
        this.enhancedAsyncClient = enhancedAsyncClient;
        this.table = enhancedAsyncClient.table("gtfsData", TableSchema.fromBean(GtfsStaticData.class));
        try (var waiter = DynamoDbWaiter.create()) {
            table.createTable();
            waiter.waitUntilTableExists(builder -> builder.tableName("gtfsData"));
        }
    }
}
