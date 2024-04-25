package com.doug.projects.transitdelayservice.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Date;
import java.util.Optional;
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

    public static long parseTimeAndApplyTimeZone(String timeString, String timeZoneString) {
        // Parse the time string
        LocalDateTime localDateTime = LocalDateTime.parse(timeString, DateTimeFormatter.ofPattern("H:mm:ss"));
        // Get the time zone
        ZoneId timeZone = ZoneId.of(timeZoneString);
        // Create a ZonedDateTime by combining time and time zone
        var time = ZonedDateTime.of(localDateTime, timeZone);
        return time.toEpochSecond();
    }

    public static Optional<Date> parseDepartureTime(long departureTime) {
        return Optional.of(new Date(departureTime * 1000));
    }
}
