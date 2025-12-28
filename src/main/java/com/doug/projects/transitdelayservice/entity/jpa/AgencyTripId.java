package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AgencyTripId {
    @Column(name = "trip_id")
    private String tripId;
    @Column(name = "agency_id")
    private String agencyId;
}