package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyRoute;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AgencyTripRepository extends JpaRepository<AgencyTrip, String> {
    @Query("select r.routeName from AgencyTrip as t join AgencyRoute as r where t.id = :tripId")
    Optional<String> findRouteNameById(String tripId);
}