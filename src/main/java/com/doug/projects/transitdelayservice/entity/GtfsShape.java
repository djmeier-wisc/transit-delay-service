package com.doug.projects.transitdelayservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GtfsShape {
    List<List<Double>> shape;
    String shapeId;
}
