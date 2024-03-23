package com.doug.projects.transitdelayservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LineGraphDataResponse {
    private Flux<LineGraphData> datasets;
    private List<String> labels;
}
