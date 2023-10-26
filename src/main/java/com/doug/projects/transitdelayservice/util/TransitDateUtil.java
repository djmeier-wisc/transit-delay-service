package com.doug.projects.transitdelayservice.util;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

public class TransitDateUtil {
    public static long getMidnightSixDaysAgo() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.minusDays(6).toLocalDate(), midnight).toEpochSecond(ZoneOffset.of("-5"));
    }

    public static long getMidnightTonight() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.plusDays(1).toLocalDate(), midnight).toEpochSecond(ZoneOffset.of("-5"));
    }
}
