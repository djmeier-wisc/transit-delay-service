package com.doug.projects.transitdelayservice.entity.transit;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyShape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import org.geojson.LngLatAlt;

import java.util.List;

@Data
@Builder
public class ShapeProperties {
    List<LngLatAlt> shapeId;
    Double delay;
    @JsonIgnore
    Integer count;
}
