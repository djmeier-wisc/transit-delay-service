package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.List;

@Repository
@Slf4j
public class AgencyRouteTimestampRepository {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<AgencyRouteTimestamp> table;

    public AgencyRouteTimestampRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table("routeTimestamp", TableSchema.fromBean(AgencyRouteTimestamp.class));
    }

    public void save(AgencyRouteTimestamp agencyRouteTimestamp) {
        table.putItem(agencyRouteTimestamp);
    }

    public void saveAll(List<AgencyRouteTimestamp> agencyRouteTimestampList) {
        if (agencyRouteTimestampList.isEmpty()) return;
        DynamoUtils.chunkList(agencyRouteTimestampList, 25)
                .forEach(chunkList -> {
                    try {
                        enhancedClient.batchWriteItem(b ->
                                chunkList.forEach(agencyRouteTimestamp -> {
                                    b.addWriteBatch(
                                            WriteBatch.builder(AgencyRouteTimestamp.class)
                                                    .mappedTableResource(table)
                                                    .addPutItem(agencyRouteTimestamp)
                                                    .build());
                                })
                        );
                        log.info("Successful write");
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to write {}", chunkList, e);
                    }

                });
    }
}
