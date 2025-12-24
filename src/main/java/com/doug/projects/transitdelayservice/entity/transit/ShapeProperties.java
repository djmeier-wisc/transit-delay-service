package com.doug.projects.transitdelayservice.entity.transit;

import lombok.Builder;
import lombok.Data;
import org.geojson.LngLatAlt;

import java.util.List;

@Data
@Builder
public class ShapeProperties {
    private List<LngLatAlt> shapeId;
    private Double delay;
    private Integer count;
    private LngLatAlt fromStop;
    private LngLatAlt toStop;
}
