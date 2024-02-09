package com.doug.projects.transitdelayservice.util;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.AbstractMap;

public class TransitDateUtil {
    public static long getMidnightSixDaysAgo() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.minusDays(6)
                        .toLocalDate(), midnight)
                .toEpochSecond(ZoneOffset.of("-5"));
    }

    public static long getMidnightTonight() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.plusDays(1)
                        .toLocalDate(), midnight)
                .toEpochSecond(ZoneOffset.of("-5"));
    }

    public static long daysToSeconds(long days) {
        return days * 24 * 60 * 60;
    }

    public static AbstractMap.SimpleEntry<Long, Long>[] getStartAndEndTimesList(long startTime, long endTime,
                                                                                int units) {
        AbstractMap.SimpleEntry<Long, Long>[] startAndEndTimesList = new AbstractMap.SimpleEntry[units];
        double distance = (double) (endTime - startTime) / units;
        for (int unit = 0; unit < units; unit++) {
            startAndEndTimesList[unit] = new AbstractMap.SimpleEntry<>(
                    startTime + (long) (distance * unit), startTime + (long) (distance * (unit + 1)));
        }
        return startAndEndTimesList;
    }
}
