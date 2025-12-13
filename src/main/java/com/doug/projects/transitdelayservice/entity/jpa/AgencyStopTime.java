package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "gtfs_stop_time")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyStopTime {

    @EmbeddedId
    private StopTimeId id;

    @MapsId("tripId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private AgencyTrip trip;

    // Foreign key to Stop
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", referencedColumnName = "id", nullable = false)
    @ToString.Exclude
    private AgencyStop stop;
    private Integer stopSeq;
    @Column
    private Integer arrivalTimeSecs;
    @Column
    private Integer departureTimeSecs;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyStopTime that = (AgencyStopTime) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    public String getTripId() {
        if(getId()==null)return null;
        return getTripId();
    }
}