package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.service.RouteMapperService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Repository
@Slf4j
public class RouteTimestampRepository {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<RouteTimestamp> delayTable;
    private final RouteMapperService routeMapperService;

    /**
     * Writes a list of delayObjects to the dynamodb
     *
     * @param routeTimestamps the delayObjects to persist
     * @return true if successful write, false if failure
     */
    public boolean writeRouteTimestamps(List<RouteTimestamp> routeTimestamps) {
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

    /**
     * Gets all RouteTimestamps between startTime and endTime for routeName.
     *
     * @param startTime the start unix time to search
     * @param endTime   the end unix time to search
     * @param routeName the friendly route name to search by
     * @return List of <code>RouteTimestamp</code>, sorted by timestamp
     */
    public List<RouteTimestamp> getRouteTimestampsBy(Number startTime, Number endTime, String routeName) {
        QueryConditional queryConditional =
                QueryConditional.sortBetween(
                        r -> r.partitionValue(routeName).sortValue(startTime),
                        r -> r.partitionValue(routeName).sortValue(endTime));
        return delayTable.query(queryConditional).items().stream().collect(Collectors.toList());
    }

    public Map<String, List<RouteTimestamp>> getRouteTimestampsMapBy(Number startTime, Number endTime) {
        return routeMapperService.getAllFriendlyNames().parallelStream().flatMap(friendlyName -> getRouteTimestampsBy(startTime,
                endTime, friendlyName).stream()).sorted(Comparator.comparing(RouteTimestamp::getTimestamp)).collect(Collectors.groupingBy(RouteTimestamp::getRoute));
    }

    public Map<String, List<RouteTimestamp>> getRouteTimestampsMapBy(Number startTime, Number endTime,
                                                                     List<String> routeNames) {
        return routeNames.parallelStream()
                .flatMap(friendlyName -> getRouteTimestampsBy(startTime, endTime, friendlyName).stream())
                .sorted(Comparator.comparing(RouteTimestamp::getTimestamp))
                .collect(Collectors.groupingBy(RouteTimestamp::getRoute));
    }

    public List<RouteTimestamp> getRouteTimestampsBy(Number startTime, Number endTime) {
        return routeMapperService.getAllFriendlyNames().parallelStream().flatMap(friendlyName -> getRouteTimestampsBy(startTime, endTime, friendlyName).stream()).collect(Collectors.toList());
    }
}
