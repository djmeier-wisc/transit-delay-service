package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyShape;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyShapeId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgencyShapeRepository extends JpaRepository<AgencyShape, AgencyShapeId> {
    List<AgencyShape> findAllById_ShapeId(String idShapeId);

    List<AgencyShape> findAllById_ShapeIdOrderByIdShapeIdAsc(String idShapeId);

    long countByAgencyTripRouteAgency_Id(String tripAgencyId);

    List<AgencyShape> findAllByAgencyTripRouteAgency_Id(String tripRouteAgencyId, Pageable pageable);
}