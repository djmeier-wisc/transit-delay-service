package com.doug.projects.transitdelayservice.repository.jpa;

import com.doug.projects.transitdelayservice.entity.Status;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AgencyFeedRepository extends JpaRepository<AgencyFeed, String> {
    List<AgencyFeed> findAllByStatus(Status status);

    @Transactional
    @Modifying
    @Query("update AgencyFeed a set a.timezone = ?1 where a.id = ?2")
    void updateTimezoneById(String timezone, String id);
    @Transactional
    @Modifying
    @Query("update AgencyFeed a set a.status = ?1 where a.id = ?2")
    void updateStatusById(Status status, String id);

    @Query(value = "SELECT status FROM AgencyFeed WHERE id = :id")
    Optional<String> findStatusById(@Param("id") String id);
}
