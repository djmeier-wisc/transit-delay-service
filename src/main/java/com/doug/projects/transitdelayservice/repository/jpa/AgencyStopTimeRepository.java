package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopTime;
import com.doug.projects.transitdelayservice.entity.jpa.StopTimeId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AgencyStopTimeRepository extends JpaRepository<AgencyStopTime, StopTimeId> {
    @EntityGraph(attributePaths = {"trips"})
    List<AgencyStopTime> findAllByTrip_IdIn(Set<String> strings);

    List<AgencyStopTime> findAllByIdInAndTripRouteAgencyId(Collection<StopTimeId> ids, String tripRouteAgencyId);
}