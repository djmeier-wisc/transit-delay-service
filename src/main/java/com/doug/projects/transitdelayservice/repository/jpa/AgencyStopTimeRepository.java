package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopTime;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopTimeId;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AgencyStopTimeRepository extends JpaRepository<AgencyStopTime, AgencyStopTimeId> {
    @EntityGraph(attributePaths = {"trip", "stop"})
    List<AgencyStopTime> findAllByTrip_IdIn(Collection<AgencyTripId> trip_id);

    List<AgencyStopTime> findAllByIdInAndTripRouteAgencyId(Collection<AgencyStopTimeId> ids, String tripRouteAgencyId);
}