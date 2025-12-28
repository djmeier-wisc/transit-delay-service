package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Entity
@Table(
        name = "gtfs_stop_time",
        schema = "MPT"
)
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyStopTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private AgencyStopTimeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "trip_id", referencedColumnName = "trip_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "agency_id", referencedColumnName = "agency_id", nullable = false, insertable = false, updatable = false)
    })
    @ToString.Exclude
    private AgencyTrip trip;

    // --- RELATIONSHIP 2: AgencyStop (Foreign Key is stop_id, agency_id) ---
    // We need a separate stop_id column for this relationship
    @Column(name = "stop_id", nullable = false)
    private String stopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "stop_id", referencedColumnName = "stop_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "agency_id", referencedColumnName = "agency_id", nullable = false, insertable = false, updatable = false)
    })
    @ToString.Exclude
    private AgencyStop stop;

    @Column
    private Integer arrivalTimeSecs;
    @Column
    private Integer departureTimeSecs;

    @Transient
    public String getTripId() {
        if (getId() == null) return null;
        return getId().getTripId();
    }

    @Transient
    public Integer getStopSeq() {
        return getId() != null ? getId().getStopSequence() : null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyStopTime that = (AgencyStopTime) o;
        // Use the generated ID for equals comparison
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        // Use the generated ID for hashing
        return Objects.hash(id);
    }
}