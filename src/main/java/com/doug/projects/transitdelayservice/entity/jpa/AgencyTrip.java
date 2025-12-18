package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "gtfs_trip", schema = "MPT")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyTrip {

    @EmbeddedId
    private AgencyTripId id;

    @Column(name = "route_id", nullable = false)
    private String routeId;

    @Column(name = "shape_id")
    private String shapeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(
                    name = "route_id",
                    referencedColumnName = "route_id",
                    nullable = false,
                    insertable = false,
                    updatable = false
            ),
            @JoinColumn(
                    name = "agency_id",
                    referencedColumnName = "agency_id",
                    nullable = false,
                    insertable = false,
                    updatable = false
            )
    })
    @ToString.Exclude
    private AgencyRoute route;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "agency_trip_shape",
            joinColumns = {
                    @JoinColumn(
                            name = "trip_id",
                            referencedColumnName = "trip_id",
                            insertable = false,
                            updatable = false
                    ),
                    @JoinColumn(
                            name = "agency_id",
                            referencedColumnName = "agency_id",
                            insertable = false,
                            updatable = false
                    )
            },
            inverseJoinColumns = {
                    @JoinColumn(
                            name = "shape_id",
                            referencedColumnName = "shape_id",
                            insertable = false,
                            updatable = false
                    ),
                    @JoinColumn(
                            name = "agency_id",
                            referencedColumnName = "agency_id",
                            insertable = false,
                            updatable = false
                    )
            }
    )
    @ToString.Exclude
    private List<AgencyShape> shapePoints;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass =
                o instanceof HibernateProxy
                        ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
                        : o.getClass();
        Class<?> thisEffectiveClass =
                this instanceof HibernateProxy
                        ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
                        : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyTrip that = (AgencyTrip) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy
                ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    public String getTripId() {
        return id != null ? id.getTripId() : null;
    }
}
