package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.*;

/**
 * Wrapper for <code>BusState</code> which also includes timestamp
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeBusState extends BusState {
    private Long timestamp;
}
