package com.doug.projects.transitdelayservice.entity;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@Builder
public class AgencyRealtimeAnalysisResponse {
    @Nullable
    private List<AgencyRouteTimestamp> routeTimestamps;
    @NotNull
    private Status feedStatus;
    private AgencyFeedDto feed;
}
