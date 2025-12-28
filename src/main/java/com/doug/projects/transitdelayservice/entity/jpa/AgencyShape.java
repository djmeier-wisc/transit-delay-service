package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Set;

@Entity
@Table(name = "gtfs_shape", schema = "MPT")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class AgencyShape {
    @Id
    private AgencyShapeId id;

    @ToString.Exclude
    @OneToMany(mappedBy = "agencyShape")
    @OrderBy("id.sequence ASC")
    private List<AgencyShapePoint> agencyShapePoints;

    @ToString.Exclude
    @OneToMany(mappedBy = "agencyShape")
    private Set<AgencyTrip> agencyTrips;

    @Column(name = "trip_id")
    private String tripId;

}
