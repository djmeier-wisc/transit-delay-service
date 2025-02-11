package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed.ID_INDEX;

@Repository
@Slf4j
public class AgencyFeedRepository {
    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final DynamoDbAsyncTable<AgencyFeed> table;
    private final CachedRepository cachedRepository;


    public AgencyFeedRepository(DynamoDbEnhancedAsyncClient enhancedClient, CachedRepository cachedRepository) {
        this.enhancedClient = enhancedClient;
        table = enhancedClient.table("agencyFeeds", TableSchema.fromBean(AgencyFeed.class));
        this.cachedRepository = cachedRepository;
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

    public List<AgencyFeed> getAgencyFeedsByStatus(AgencyFeed.Status... statusList) {
        return Flux.concat(Arrays.stream(statusList).map(status -> table
                        .query(q ->
                                q.queryConditional(
                                        QueryConditional.keyEqualTo(k -> k.partitionValue(status.toString()))))
                        .items()).toList())
                .collectList()
                .toFuture()
                .join();
    }

    public Flux<AgencyFeed> getAgencyFeedsByStatusFlux(AgencyFeed.Status... statusList) {
        return Flux.fromArray(statusList).flatMap(s ->
                table.query(q -> q.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(s.toString()))))
        ).flatMapIterable(Page::items);
    }

    public Flux<AgencyFeed> getFeedFluxByStatus(AgencyFeed.Status status) {
        return Flux.concat(table
                .query(q ->
                        q.queryConditional(
                                QueryConditional.keyEqualTo(k -> k.partitionValue(status.toString()))))
                .items());
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

    public Mono<AgencyFeed> getAgencyFeedById(String feedId, boolean useCache) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(feedId).build());
        if (useCache) {
            return cachedRepository.getCachedQuery(table.index(ID_INDEX), queryConditional).next();
        }
        return Flux.concat(table.index(ID_INDEX).query(queryConditional))
                .flatMapIterable(Page::items)
                .next();
    }
}
