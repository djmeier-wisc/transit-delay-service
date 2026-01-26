package com.doug.projects.transitdelayservice.entity.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Entity
@Table(
        name = "gtfs_trip_delay",
        schema = "MPT",
        // Enforce uniqueness constraint on the required tracking fields:
        // A single delay measurement for a trip, at a specific stop, from an agency, at a specific time.
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trip_delay_trip_agency_stop_time",
                        columnNames = {"trip_id", "agency_id", "timestamp"}
                )
        },
        indexes = {
                        // index optimized for lookups by trip id then timestamp when filtering on trip sets
                @Index(name = "idx_adr_agency_trip_timestamp", columnList = "agency_id, trip_id, timestamp"),
                @Index(name = "idx_adr_trip_id", columnList = "trip_id")
        }
)
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyTripDelay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "trip_id", nullable = false)
    private String tripId;

    @Column(name = "agency_id", nullable = false)
    private String agencyId;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "stop_id", nullable = false)
    private String stopId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "stop_id", referencedColumnName = "stop_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "agency_id", referencedColumnName = "agency_id", nullable = false, insertable = false, updatable = false)
    })
    private AgencyStop agencyStop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "trip_id", referencedColumnName = "trip_id", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "agency_id", referencedColumnName = "agency_id", nullable = false, insertable = false, updatable = false)
    })
    @ToString.Exclude
    private AgencyTrip trip;
    private Integer delaySeconds;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyTripDelay that = (AgencyTripDelay) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}