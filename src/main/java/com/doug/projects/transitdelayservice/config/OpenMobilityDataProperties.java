package com.doug.projects.transitdelayservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "open-mobility-data")
public class OpenMobilityDataProperties {
    private List<HardcodedFeed> hardcodedFeeds = new ArrayList<>();

    @Data
    public static class HardcodedFeed {
        private String staticUrl;
        private String rtTu;
    }
}
