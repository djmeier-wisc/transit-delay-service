package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgencyFeed {
    @Getter(onMethod = @__(@DynamoDbPartitionKey))
    private String status;
    @Getter(onMethod = @__(@DynamoDbSortKey))
    private String id;
    private String name;
    private String realTimeUrl;
    private String staticUrl;
    private String state;

    public enum Status {
        ACTIVE("ACT"),
        UNAUTHORIZED("UNAUTHORIZED"), //token missing / 401 / 403 status
        UNAVAILABLE("UNAVAILABLE"), //generic connection error
        OUTDATED("OUTDATED"), //used when we are unable to find the routeId sent in a realtime response
        TIMEOUT("TIMEOUT"), //used when we are not able to download the feed in a certain amount of time
        DELETED("DEL");

        private final String name;

        Status(String s) {
            name = s;
        }

        public String toString() {
            return this.name;
        }
    }
}
