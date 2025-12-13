package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "GTFS_ROUTE", schema = "MPT")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyRoute {

    // Primary key: The route_id
    @Id
    private String id;

    // Foreign key to Agency
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private AgencyFeed agency;

    private String routeName;
    private String routeColor;
    private Integer routeSortOrder;

    @ToString.Exclude
    @OneToMany(mappedBy = "route", orphanRemoval = true)
    private List<AgencyTrip> agencyTrips;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyRoute route = (AgencyRoute) o;
        return getId() != null && Objects.equals(getId(), route.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
