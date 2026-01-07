package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelay;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelayId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgencyTripDelayRepository extends JpaRepository<AgencyTripDelay, AgencyTripDelayId> {
    @Query("""
            SELECT new com.doug.projects.transitdelayservice.repository.jpa.AgencyTripDelayDto(r.routeName,adr.timestamp, adr.delaySeconds, adr.stopId, adr.tripId) FROM AgencyTripDelay adr \
            JOIN adr.trip t \
            JOIN t.route r \
            WHERE adr.agencyId = :agencyId \
            AND r.routeName IN :routeNames \
            AND adr.timestamp BETWEEN :startTime AND :endTime \
            ORDER BY adr.timestamp ASC""")
    List<AgencyTripDelayDto> findDelayRecordsForRoutesAndTimeRange(
            @Param("agencyId") String agencyId,
            @Param("routeNames") List<String> routeNames,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime
    );
}