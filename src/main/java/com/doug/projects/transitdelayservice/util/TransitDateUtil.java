package com.doug.projects.transitdelayservice.util;

import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class TransitDateUtil {
    static long TWENTY_FOUR_HOURS_IN_SECONDS = 24 * 60 * 60;
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

    public static int calculateTimeDifferenceInSeconds(String expectedTimeSecs, long actualTimestamp, String timeZoneId) {
        return calculateTimeDifferenceInSeconds(convertGtfsTimeToSeconds(expectedTimeSecs),actualTimestamp,timeZoneId);
    }

    /**
     * Performs actualTime - expectedTime, resolving the expected time against the
     * service date implied by the actual timestamp.
     *
     * @param expectedTimeSecs The expected arrival/departure time as the total number of
     * seconds since midnight of the service day (e.g., 91800 for 25:30:00).
     * @param actualTimestamp  The actual arrival/departure time, in epoch seconds (long).
     * @param timeZoneId       The timezone to parse this in, IE "America/Chicago".
     * @return The number of seconds difference: positive for delay (late), negative for early.
     * @throws DateTimeException if the timezone ID is invalid.
     */
    public static int calculateTimeDifferenceInSeconds(Integer expectedTimeSecs, long actualTimestamp, String timeZoneId)
            throws DateTimeException {

        if (expectedTimeSecs == null) {
            throw new IllegalArgumentException("Expected time (in seconds) cannot be null.");
        }
        ZoneId timezone = ZoneId.of(timeZoneId);
        Instant actualInstant = Instant.ofEpochSecond(actualTimestamp);
        ZonedDateTime actualZonedDateTime = actualInstant.atZone(timezone);
        ZonedDateTime midnightOfServiceDay = actualZonedDateTime
                .toLocalDate()
                .atStartOfDay(timezone)
                .truncatedTo(ChronoUnit.SECONDS);

        ZonedDateTime expectedZonedDateTime = midnightOfServiceDay.plusSeconds(expectedTimeSecs);


        long expectedTimestamp = expectedZonedDateTime.toEpochSecond();

        long differenceInSeconds = actualTimestamp - expectedTimestamp;

        if(differenceInSeconds > TWENTY_FOUR_HOURS_IN_SECONDS / 2) {
            differenceInSeconds -= TWENTY_FOUR_HOURS_IN_SECONDS;
        } else if (differenceInSeconds < -TWENTY_FOUR_HOURS_IN_SECONDS / 2) {
            differenceInSeconds += TWENTY_FOUR_HOURS_IN_SECONDS;
        }

        return (int) differenceInSeconds;
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

    /**
     * Converts a GTFS time string (HH:mm:ss, potentially > 24 hours)
     * into the total number of seconds since midnight of the service day.
     * @param gtfsTimeString The arrival/departure time (e.g., "25:30:00").
     * @return Total seconds since midnight.
     */
    public static int convertGtfsTimeToSeconds(String gtfsTimeString) {
        String[] parts = gtfsTimeString.split(":");
        if (parts.length != 3) {
            // Handle error or invalid format
            return 0;
        }

        long hours = Long.parseLong(parts[0]);
        long minutes = Long.parseLong(parts[1]);
        long seconds = Long.parseLong(parts[2]);

        return (int) Duration.ofHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds)
                .getSeconds();
    }
}
