package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@Slf4j
public class AgencyFeedRepository {
    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncTable<AgencyFeed> table;


    public AgencyFeedRepository(DynamoDbEnhancedAsyncClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        table = enhancedClient.table("agencyFeeds", TableSchema.fromBean(AgencyFeed.class));
    }


    public void writeAgencyFeeds(List<AgencyFeed> agencyFeedList) {
        DynamoUtils.chunkList(agencyFeedList, 25).forEach(list -> {
            Set<String> seenIds = new HashSet<>();
            list.removeIf(feed -> !seenIds.add(feed.getId()));
            enhancedClient.batchWriteItem(b -> {
                        for (var feed : list) {
                            b.addWriteBatch(WriteBatch.builder(AgencyFeed.class).mappedTableResource(table).addPutItem(feed).build());
                        }
                    })
                    .join();
        });
    }

    public void writeAgencyFeed(AgencyFeed agencyFeed) {
        table.putItem(agencyFeed)
                .join();
    }

    public void removeAgencyFeeds(List<AgencyFeed> agencyFeedList) {
        agencyFeedList.parallelStream()
                .forEach(this::removeAgencyFeed);
    }

    public void removeAgencyFeed(AgencyFeed feed) {
        if (feed == null) {
            log.error("TRIED TO REMOVE NULL FEED");
            return;
        }
        table.deleteItem(Key.builder()
                        .partitionValue(feed.getStatus())
                        .sortValue(feed.getId())
                        .build())
                .join();
    }

    public List<AgencyFeed> getAgencyFeedsByStatus(AgencyFeed.Status status) {
        return Flux.concat(table
                        .query(q ->
                                q.queryConditional(
                                        QueryConditional.keyEqualTo(k -> k.partitionValue(status.toString()))))
                        .items())
                .collectList()
                .toFuture()
                .join();
    }

    public List<AgencyFeed> getAllAgencyFeeds() {
        return Flux.concat(table.scan().items())
                .collectList()
                .toFuture()
                .join();
    }

    public void removeAllAgencyFeeds() {
        Flux.concat(table.scan().items())
                .collectList()
                .toFuture()
                .thenAccept(this::removeAgencyFeeds)
                .join();
    }
}
