package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class AgencyShapeId implements Serializable {
    @Column(name = "shape_id")
    private String shapeId;
    @Column(name = "shape_sequence")
    private Integer sequence;
    @Column(name = "agency_id")
    private String agencyId;
}
