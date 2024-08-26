package com.doug.projects.transitdelayservice.entity.transit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShapeProperties {
    String shapeId;
    Double delay;
}
