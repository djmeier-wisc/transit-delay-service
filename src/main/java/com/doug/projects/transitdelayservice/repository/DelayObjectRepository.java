package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.List;

@AllArgsConstructor
@Repository
@Slf4j
public class DelayObjectRepository {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<RouteTimestamp> delayTable;

    /**
     * Writes a list of delayObjects to the dynamodb
     *
     * @param routeTimestamps the delayObjects to persist
     * @return true if successful write, false if failure
     */
    public boolean writeToDb(List<RouteTimestamp> routeTimestamps) {
        if (routeTimestamps.isEmpty()) return true;
        try {
            while (!routeTimestamps.isEmpty()) {
                int maxDynamoWriteIndex = 24;
                int lastIndexOfList = routeTimestamps.size();
                if (lastIndexOfList < maxDynamoWriteIndex) {
                    maxDynamoWriteIndex = lastIndexOfList;
                }
                List<RouteTimestamp> subList = routeTimestamps.subList(0, maxDynamoWriteIndex);
                //for some god-forsaken reason, we can only write 25 items at a time to dynamo
                enhancedClient.batchWriteItem(r -> {
                    WriteBatch.Builder<RouteTimestamp> builder = WriteBatch.builder(RouteTimestamp.class);
                    subList.forEach(builder::addPutItem);
                    builder.mappedTableResource(delayTable);
                    r.addWriteBatch(builder.build());
                });
                routeTimestamps.removeAll(subList);
            }
            log.info("Completed write successfully!");
            return true;
        } catch (Exception e) {
            log.error("Failed to write delayObjects", e);
            return false;
        }
    }
}
