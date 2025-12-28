package com.doug.projects.transitdelayservice.entity.jpa;

import com.doug.projects.transitdelayservice.entity.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "AGENCY_FEED", schema = "MPT", indexes = {
        @Index(name = "idx_agencyfeed_status", columnList = "status")
})
public class AgencyFeed {
    @Id
    @Column(name = "agency_id")
    private String id;
    @Enumerated
    @Column(length = 10)
    private Status status;
    private String name;
    private String realTimeUrl;
    private String staticUrl;
    private String state;
    private String timezone;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        AgencyFeed that = (AgencyFeed) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
