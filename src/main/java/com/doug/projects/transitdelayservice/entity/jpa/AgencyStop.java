package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "gtfs_stop", schema = "MPT")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyStop {
    @EmbeddedId
    private AgencyStopId id;

    private String stopName;
    private Double stopLat;
    private Double stopLon;

    @OneToMany(mappedBy = "agencyStop")
    @ToString.Exclude
    private Set<AgencyTripDelay> agencyTripDelays;


    @ToString.Exclude
    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "agency_id", nullable = false, insertable = false, updatable = false)
    private AgencyFeed agencyFeed;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyStop stop = (AgencyStop) o;
        return getId() != null && Objects.equals(getId(), stop.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }

    public String getStopId() {
        if (getId() == null) return null;
        return getId().getStopId();
    }
}