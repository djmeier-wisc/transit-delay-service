package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class AgencyShapeId {
    @Column(name = "agency_id")
    private String agencyId;
    @Column(name = "shape_id")
    private String shapeId;
}
