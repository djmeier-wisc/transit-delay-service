package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyRoute;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyRouteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AgencyRouteRepository extends JpaRepository<AgencyRoute, AgencyRouteId> {
    @Query("select routeName from AgencyRoute where id = :routeId")
    Optional<String> findRouteNameById(AgencyRouteId routeId);

    List<AgencyRoute> findAllByAgency_Id(String agencyId);

    @Query("""
                select r.routeName
                from AgencyRoute r
                where r.id.agencyId = :agencyId
                and exists (select 1 from AgencyTripDelay d where d.trip.route = r)
                group by r.routeName
                order by min(r.routeSortOrder) asc, r.routeName asc
            """)
    List<String> findAllByAgencyIdFiltered(String agencyId);
}