package com.doug.projects.transitdelayservice.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Objects;

@DynamoDbBean
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class DelayObject {


    //the time measured
    String id;
    Integer delay;
    Long metroTimestamp;
    String route;
    String stopId;

    public String getRunNumber() {
        return runNumber;
    }

    public void setRunNumber(String runNumber) {
        this.runNumber = runNumber;
    }

    String runNumber;

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    @DynamoDbSortKey
    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }


    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
    }


    public Long getMetroTimestamp() {
        return metroTimestamp;
    }

    public void setMetroTimestamp(Long metroTimestamp) {
        this.metroTimestamp = metroTimestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
