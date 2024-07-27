package com.doug.projects.transitdelayservice.entity.transit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DelayAndShapeId {
    String shapeId;
    Double delay;
}
