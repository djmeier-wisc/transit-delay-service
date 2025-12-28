package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyStop;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyStopId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AgencyStopRepository extends JpaRepository<AgencyStop, AgencyStopId> {
    List<AgencyStop> findAllByIdIn(Set<AgencyStopId> ids);

    @Query("""
            select s.id.stopId from AgencyStop s where s.id in :ids
            """)
    Set<String> findStopIdByIdIn(Collection<AgencyStopId> ids);
}