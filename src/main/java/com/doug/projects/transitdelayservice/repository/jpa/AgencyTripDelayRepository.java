package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelay;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelayId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgencyTripDelayRepository extends JpaRepository<AgencyTripDelay, AgencyTripDelayId> {
    @Query("""
            SELECT adr FROM AgencyTripDelay adr \
            JOIN FETCH adr.trip t \
            JOIN FETCH t.route r \
            JOIN FETCH adr.agencyStop \
            JOIN r.agency a \
            WHERE a.id = :agencyId \
            AND r.routeName IN :routeNames \
            AND adr.timestamp BETWEEN :startTime AND :endTime \
            ORDER BY adr.timestamp ASC""")
    List<AgencyTripDelay> findDelayRecordsForRoutesAndTimeRange(
            @Param("agencyId") String agencyId,
            @Param("routeNames") List<String> routeNames,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime
    );
}