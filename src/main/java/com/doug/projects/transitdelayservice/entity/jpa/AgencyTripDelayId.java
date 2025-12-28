package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgencyTripDelayId implements Serializable {
    @Column(name = "trip_id")
    @NotNull
    private String tripId;
    @Column(name = "timestamp")
    @NotNull
    private Long timestamp;
    @Column(name = "agency_id")
    private String agencyId;
}