package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyShapePoint;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyShapePointId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyShapePointRepository extends JpaRepository<AgencyShapePoint, AgencyShapePointId> {

}