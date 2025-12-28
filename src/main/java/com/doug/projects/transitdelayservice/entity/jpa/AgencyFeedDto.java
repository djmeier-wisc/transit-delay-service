package com.doug.projects.transitdelayservice.entity.jpa;

import com.doug.projects.transitdelayservice.entity.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * DTO for {@link AgencyFeed}
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgencyFeedDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 0L;
    private String id;
    private Status status;
    private String name;
    private String realTimeUrl;
    private String staticUrl;
    private String state;
    private String timezone;

}