package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShapePointId implements Serializable {
    @Column(name = "shape_id")
    private String shapeId;
    @Column(name = "shape_sequence")
    private Integer sequence;
}
