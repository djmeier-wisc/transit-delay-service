package com.doug.projects.transitdelayservice.entity.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SequencedData {
    int sequenceNo;
    String departureTime;
    String arrivalTime;
    String stopId;
    String stopName;
    Double shapeLat;
    Double shapeLon;
}
