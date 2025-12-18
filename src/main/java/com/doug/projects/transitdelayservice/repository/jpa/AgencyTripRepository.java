package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyTrip;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AgencyTripRepository extends JpaRepository<AgencyTrip, AgencyTripId> {
    @Query("select r.routeName from AgencyTrip as t join AgencyRoute r on t.routeId = r.id.routeId and t.id.agencyId = r.id.agencyId where t.id = :tripId")
    Optional<String> findRouteNameById(AgencyTripId tripId);
}