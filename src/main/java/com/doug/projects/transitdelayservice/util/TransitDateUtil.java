package com.doug.projects.transitdelayservice.util;

import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.util.AbstractMap;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class TransitDateUtil {
    public static long getMidnightSixDaysAgo() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.minusDays(6)
                        .toLocalDate(), midnight)
                .toEpochSecond(ZoneOffset.of("-5"));
    }

    public static long getMidnightOneMonthAgo() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.minusMonths(1).toLocalDate(), midnight).toEpochSecond(ZoneOffset.of("-5"));
    }

    public static long getMidnightTonight() {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.plusDays(1)
                        .toLocalDate(), midnight)
                .toEpochSecond(ZoneOffset.of("-5"));
    }

    public static long getMidnightDaysAgo(int daysAgo) {
        LocalDateTime ldt = LocalDateTime.now();
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(ldt.minusDays(daysAgo).toLocalDate(), midnight).toEpochSecond(ZoneOffset.of("-5"));
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
     * Performs actualTime - expectedTime. For example, if actualTime is 12:32 and expectedTime is 12:25, this would
     * return 7 (mins) * 60 (seconds in min)
     *
     * @param expectedTime    The expected arrival/departure of a bus, in format H:mm:ss
     * @param actualTimestamp The actual arrival/departure time of a bus, in epoch seconds
     * @param timeZoneId      The timezone to parse this in, IE "America/Chicago"
     * @return the number of seconds difference.
     */
    public static long calculateTimeDifferenceInSeconds(String expectedTime, long actualTimestamp, String timeZoneId) throws DateTimeException {
        ZoneId timezone = ZoneId.of(timeZoneId);
        expectedTime = replaceGreaterThan24Hr(expectedTime);
        expectedTime = addLeadingZero(expectedTime);
        LocalTime time = LocalTime.parse(expectedTime);
        LocalDate date = LocalDate.ofInstant(Instant.ofEpochSecond(actualTimestamp), timezone);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(date, time, timezone);
        return actualTimestamp - zonedDateTime.toEpochSecond();
    }

    private static String addLeadingZero(String expectedTime) {
        String[] split = expectedTime.split(":");
        if (split.length != 0 && split[0].length() < 2) {
            return '0' + expectedTime;
        }
        return expectedTime;
    }

    public static @NotNull String replaceGreaterThan24Hr(String expectedTime) {
        String hr = expectedTime.split(":")[0];
        if (toInt(hr) > 23) {
            String newHr = String.valueOf(toInt(hr) % 24);
            if (newHr.length() == 1) {
                newHr = "0" + newHr;
            }
            expectedTime = expectedTime.replace(hr, newHr);
        }
        return expectedTime;
    }
}
