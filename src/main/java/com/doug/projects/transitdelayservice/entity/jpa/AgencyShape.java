package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Entity
@Table(name = "gtfs_shape_point")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class AgencyShape {

    @EmbeddedId
    private ShapePointId id;

    // Renamed fields for clarity (to match GTFS shape_pt_lat/lon)
    @Column(name = "shape_pt_lat")
    private Double shapePtLat;

    @Column(name = "shape_pt_lon")
    private Double shapePtLon;

    @ToString.Exclude
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "agency_trip_id")
    private AgencyTrip agencyTrip;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyShape that = (AgencyShape) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
