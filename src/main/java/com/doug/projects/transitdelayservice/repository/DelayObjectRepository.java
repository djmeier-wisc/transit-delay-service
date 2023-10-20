package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.DelayObject;
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
    private final DynamoDbTable<DelayObject> delayTable;

    /**
     * Writes a list of delayObjects to the dynamodb
     *
     * @param delayObjects the delayObjects to persist
     */
    public void writeToDb(List<DelayObject> delayObjects) {
        log.info("writing delayObjects:{}",delayObjects);
        try {
            while(!delayObjects.isEmpty()) {
                int maxDynamoWriteIndex = 24;
                int lastIndexOfList = delayObjects.size();
                if(lastIndexOfList < maxDynamoWriteIndex) {
                    maxDynamoWriteIndex = lastIndexOfList;
                }
                List<DelayObject> subList = delayObjects.subList(0,maxDynamoWriteIndex);
                //for some god forsaken reason, we can only write 25 items at a time to dynamo
                enhancedClient.batchWriteItem(r -> {
                    WriteBatch.Builder<DelayObject> builder = WriteBatch.builder(DelayObject.class);
                subList.forEach(builder::addPutItem);
                    builder.mappedTableResource(delayTable);
                    r.addWriteBatch(builder.build());
                });
                delayObjects.removeAll(subList);
            }
            log.info("Completed write successfully!");
        } catch (Exception e) {
            log.error("Failed to write delayObjects",e);
        }
    }
}
