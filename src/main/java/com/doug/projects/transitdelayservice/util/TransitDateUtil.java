package com.doug.projects.transitdelayservice.util;

import java.time.*;
import java.util.AbstractMap;

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

    /**
     * Performs actualTime - expectedTime.
     * For example, if actualTime is 12:32 and expectedTime is 12:25, this would return 7 (mins) * 60 (seconds in min)
     *
     * @param expectedTime    The expected arrival/departure of a bus, in format H:mm:ss
     * @param actualTimestamp The actual arrival/departure time of a bus, in epoch seconds
     * @param timeZoneId      The timezone to parse this in, IE "America/Chicago"
     * @return the number of seconds difference.
     */
    public static long calculateTimeDifferenceInSeconds(String expectedTime, long actualTimestamp, String timeZoneId) throws DateTimeException {
        ZoneId timezone = ZoneId.of(timeZoneId);
        LocalTime time = LocalTime.parse(expectedTime);
        LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(actualTimestamp), timezone);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(date, time, timezone);
        return actualTimestamp - zonedDateTime.toEpochSecond();
    }
}
