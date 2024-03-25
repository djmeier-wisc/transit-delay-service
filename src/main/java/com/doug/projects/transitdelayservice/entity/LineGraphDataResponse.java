package com.doug.projects.transitdelayservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LineGraphDataResponse {
    private List<LineGraphData> datasets;
    private List<String> labels;
}
