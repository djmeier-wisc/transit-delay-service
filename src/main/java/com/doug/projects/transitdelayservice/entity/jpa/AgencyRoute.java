package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "gtfs_route", schema = "MPT",
       indexes = {@Index(name = "idx_route_agency_name", columnList = "agency_id, route_name"),
                  @Index(name = "idx_route_agency_sort", columnList = "agency_id, route_sort_order")})
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyRoute {
    @EmbeddedId
    private AgencyRouteId id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", referencedColumnName = "agency_id", nullable = false, insertable = false, updatable = false)
    @ToString.Exclude
    private AgencyFeed agency;

    private String routeName;
    private String routeColor;
    private Integer routeSortOrder;

    @ToString.Exclude
    @OneToMany(mappedBy = "route")
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

    public String getRouteId() {
        if (getId() == null) return null;
        return getId().getRouteId();
    }
}
