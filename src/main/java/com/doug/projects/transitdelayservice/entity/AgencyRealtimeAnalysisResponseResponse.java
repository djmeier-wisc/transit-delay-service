package com.doug.projects.transitdelayservice.entity;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyRouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.Status;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@Builder
public class AgencyRealtimeAnalysisResponseResponse {
    @Nullable
    private List<AgencyRouteTimestamp> routeTimestamps;
    @NotNull
    private Status feedStatus;
    private AgencyFeedDto feed;
}
