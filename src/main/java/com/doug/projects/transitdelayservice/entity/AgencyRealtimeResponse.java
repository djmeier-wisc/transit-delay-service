package com.doug.projects.transitdelayservice.entity;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@Builder
public class AgencyRealtimeResponse {
    @Nullable
    private List<AgencyRouteTimestamp> routeTimestamps;
    @NotNull
    private AgencyFeed.Status feedStatus;
    private AgencyFeed feed;
}
