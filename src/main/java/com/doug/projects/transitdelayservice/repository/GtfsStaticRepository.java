package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.util.List;

@Repository
public class GtfsStaticRepository {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<GtfsStaticData> table;

    public GtfsStaticRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table("gtfsData", TableSchema.fromBean(GtfsStaticData.class));
        try (var waiter = DynamoDbWaiter.create()) {
            table.createTable();
            waiter.waitUntilTableExists(builder -> builder.tableName("gtfsData"));
        }
    }

    public void save(GtfsStaticData data) {
        table.putItem(data);
    }

    public void saveAll(List<GtfsStaticData> data) {
        for (List<GtfsStaticData> chunkedList : DynamoUtils.chunkList(data, 25)) {
            enhancedClient.batchWriteItem(b -> {
                for (var item : chunkedList) {
                    b.addWriteBatch(WriteBatch.builder(GtfsStaticData.class).mappedTableResource(table).addPutItem(item).build());
                }
            });
        }
    }
}
