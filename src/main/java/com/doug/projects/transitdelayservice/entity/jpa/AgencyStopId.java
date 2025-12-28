package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
public class AgencyStopId {
    @Column(name = "stop_id")
    private String stopId;
    @Column(name = "agency_id")
    private String agencyId;
}