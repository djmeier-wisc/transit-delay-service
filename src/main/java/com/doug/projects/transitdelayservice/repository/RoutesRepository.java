package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRoute;
import com.doug.projects.transitdelayservice.util.DynamoUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RoutesRepository {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<AgencyRoute> table;

    public RoutesRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table("routes", TableSchema.fromBean(AgencyRoute.class));
    }

    public void saveAll(List<AgencyRoute> agencyRouteList) {
        DynamoUtils.chunkList(agencyRouteList, 25).forEach(list -> {
            Set<String> seenIds = new HashSet<>();
            list.removeIf(route -> !seenIds.add(route.getAgencyId() + route.getRouteId()));
            enhancedClient.batchWriteItem(b -> {
                for (var route : list) {
                    b.addWriteBatch(WriteBatch.builder(AgencyRoute.class).mappedTableResource(table).addPutItem(route).build());
                }
            });
        });
    }

    /**
     * Gets the AgencyRoute by agencyId and routeId.
     *
     * @param agencyId The agencyId of the route.
     * @param routeId  The routeId of the route.
     * @return The AgencyRoute object.
     */
    public Optional<AgencyRoute> getAgencyRoute(String agencyId, String routeId) {
        if (StringUtils.isBlank(agencyId) || StringUtils.isBlank(routeId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(agencyId).sortValue(routeId))));
    }

    /**
     * Gets all AgencyRoutes for the agencyId.
     *
     * @param agencyId The agencyId of the routes.
     * @return A list of AgencyRoute objects.
     */
    public List<AgencyRoute> getAgencyRoutes(String agencyId) {
        return table.scan().items().stream().filter(r -> r.getAgencyId().equals(agencyId)).toList();
    }
}
