package com.doug.projects.transitdelayservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.doug.projects.transitdelayservice.config.CachingConfiguration.DYNAMO_REPOSITORY_CACHE;

@Repository
@RequiredArgsConstructor
public class CachedRepository {
    private final CacheManager cacheManager;
    private final DynamoDbEnhancedAsyncClient asyncClient;

    @Cacheable(value = DYNAMO_REPOSITORY_CACHE)
    public <T> Flux<T> getCachedQuery(DynamoDbAsyncIndex<T> asyncIndex, QueryConditional queryConditional) {
        return Flux.from(asyncIndex.query(queryConditional)).flatMapIterable(Page::items).cache();
    }

    @Cacheable(value = DYNAMO_REPOSITORY_CACHE)
    public <T> Flux<T> getCachedQuery(DynamoDbAsyncTable<T> asyncTable, QueryConditional queryConditional) {
        return Flux.from(asyncTable.query(queryConditional)).flatMapIterable(Page::items).cache();
    }

    public <T> Flux<T> getCachedQuery(DynamoDbAsyncTable<T> table, List<Key> keys, Class<T> clazz, Function<T, Key> keyGenerator) {
        var cache = cacheManager.getCache(DYNAMO_REPOSITORY_CACHE);
        if (cache == null) {
            return batchGetItems(table, keys, clazz); //if cache isn't configured, run as normal
        }
        List<Key> notCachedKeys = new ArrayList<>();
        List<T> cachedResult = new ArrayList<>();
        for (Key k : keys) {
            var cachedValue = cache.get(k, clazz);
            if (cachedValue == null) {
                notCachedKeys.add(k);
            } else {
                cachedResult.add(cachedValue);
            }
        }
        Flux<T> cachedValues = Flux.fromIterable(cachedResult);
        Flux<T> nonCachedValues = batchGetItems(table, notCachedKeys, clazz)
                .doOnNext(nonCachedResult ->
                        cache.put(keyGenerator.apply(nonCachedResult), nonCachedResult) //put all non-cached values into cache when flux is merged
                );
        return Flux.merge(cachedValues, nonCachedValues);
    }

    private <T> Flux<T> batchGetItems(DynamoDbAsyncTable<T> table, List<Key> keys, Class<T> clazz) {
        if (keys.isEmpty()) return Flux.empty();
        var readBatches = keys.stream()
                .map(s -> ReadBatch.builder(clazz).mappedTableResource(table).addGetItem(s).build())
                .toList();
        var asyncResult = DynamoUtils.chunkList(readBatches, 100)
                .stream()
                .map(chunk -> asyncClient.batchGetItem(BatchGetItemEnhancedRequest.builder().readBatches(chunk).build()))
                .map(batchGetResultPagePublisher -> batchGetResultPagePublisher.resultsForTable(table))
                .toList();
        return Flux.fromIterable(asyncResult)
                .flatMap(Flux::from);
    }
}
