package com.doug.projects.transitdelayservice.entity;

import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class MapOptions {
    String routeName;
    int searchPeriod = 30;
    int hourStarted = 0;
    int hourEnded = 23;
    Set<Integer> daysSelected = new HashSet<>(List.of(1, 2, 3, 4, 5, 6, 7));
}
