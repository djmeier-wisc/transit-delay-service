package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyStopRepository extends JpaRepository<AgencyStop, String> {
}