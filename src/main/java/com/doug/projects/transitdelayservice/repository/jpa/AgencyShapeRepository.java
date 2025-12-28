package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyShape;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyShapeId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgencyShapeRepository extends JpaRepository<AgencyShape, AgencyShapeId> {
    long countById_AgencyId(String idAgencyId);

    @EntityGraph(attributePaths = {"agencyShapePoints"})
    List<AgencyShape> findAllById_AgencyId(String idAgencyId, Pageable pageable);
}