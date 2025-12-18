package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AgencyStopTimeId {
    @Column(name = "trip_id")
    private String tripId;
    @Column(name = "stop_seq")
    private Integer stopSequence;
    @Column(name = "agency_id")
    private String agencyId;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyStopTimeId that = (AgencyStopTimeId) o;
        return getTripId() != null && Objects.equals(getTripId(), that.getTripId())
                && getStopSequence() != null && Objects.equals(getStopSequence(), that.getStopSequence());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(tripId, stopSequence);
    }
}