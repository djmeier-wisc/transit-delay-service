package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyRoute {
    public static final AgencyRoute UNKNOWN_ROUTE = AgencyRoute.builder().agencyId("UNKNOWN").routeId("UNKNOWN").routeName("UNKNOWN").build();
    @Getter(AccessLevel.NONE)
    private String agencyId;
    @Getter(AccessLevel.NONE)
    private String routeId;
    @Getter(AccessLevel.NONE)
    private String routeName;
    private String color;
    private Integer sortOrder;

    @DynamoDbPartitionKey
    @DynamoDbSecondaryPartitionKey(indexNames = "agencyId-routeName-index")
    public String getAgencyId() {
        return agencyId;
    }

    @DynamoDbSortKey
    public String getRouteId() {
        return routeId;
    }

    @DynamoDbSecondarySortKey(indexNames = "agencyId-routeName-index")
    public String getRouteName() {
        return routeName;
    }
}
