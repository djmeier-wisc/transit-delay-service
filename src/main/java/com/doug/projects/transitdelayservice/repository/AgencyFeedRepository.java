package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class AgencyFeedRepository {
    private final DynamoDbEnhancedClient enhancedClient;

    public void writeAgencyFeeds(List<AgencyFeed> agencyFeedList) {
        DynamoDbTable<AgencyFeed> table = enhancedClient.table("agencyFeeds", TableSchema.fromBean(AgencyFeed.class));
        DynamoUtils.chunkList(agencyFeedList, 25).forEach(list -> {
            Set<String> seenIds = new HashSet<>();
            list.removeIf(feed -> !seenIds.add(feed.getId()));
            enhancedClient.batchWriteItem(b -> {
                for (var feed : list) {
                    b.addWriteBatch(WriteBatch.builder(AgencyFeed.class).mappedTableResource(table).addPutItem(feed).build());
                }
            });
        });
    }

    public List<AgencyFeed> getACTStatusAgencyFeeds() {
        DynamoDbTable<AgencyFeed> table = enhancedClient.table("agencyFeeds", TableSchema.fromBean(AgencyFeed.class));
        return table.query(q ->
                        q.queryConditional(
                                        QueryConditional.keyEqualTo(k -> k.partitionValue("ACT")))
                                .build()).
                items().stream().toList();
    }
}
