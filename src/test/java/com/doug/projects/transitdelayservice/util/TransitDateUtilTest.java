package com.doug.projects.transitdelayservice.util;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.AbstractMap;

import static com.doug.projects.transitdelayservice.util.TransitDateUtil.calculateTimeDifferenceInSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitDateUtilTest {

    @Test
    void testGetMidnightSixDaysAgo() {
        long result = TransitDateUtil.getMidnightSixDaysAgo();
        long expected = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0)
                .toEpochSecond(ZoneOffset.of("-5"));
        assertEquals(expected, result, "Midnight six days ago calculation is incorrect");
    }

    @Test
    void testGetMidnightTonight() {
        long result = TransitDateUtil.getMidnightTonight();
        long expected = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0)
                .toEpochSecond(ZoneOffset.of("-5"));
        assertEquals(expected, result, "Midnight tonight calculation is incorrect");
    }

    @Test
    void testGetStartAndEndTimesList() {
        long startTime = LocalDateTime.now().toEpochSecond(ZoneOffset.of("-5"));
        long endTime = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.of("-5"));
        int units = 5;

        AbstractMap.SimpleEntry<Long, Long>[] result =
                TransitDateUtil.getStartAndEndTimesList(startTime, endTime, units);

        // Ensure the result array has the correct length
        assertEquals(units, result.length, "Start and end times list has incorrect length");

        // Ensure the start and end times are calculated correctly
        for (int unit = 0; unit < units; unit++) {
            long expectedStart = startTime + ((endTime - startTime) / units * unit);
            long expectedEnd = startTime + ((endTime - startTime) / units * (unit + 1));
            assertEquals(expectedStart, result[unit].getKey(), "Start time for unit " + unit + " is incorrect");
            assertEquals(expectedEnd, result[unit].getValue(), "End time for unit " + unit + " is incorrect");
        }
    }

    @Test
    void calculateTimeDifferenceInSeconds_lateBus() {
        // 1. Actual Time: 2024-09-14 00:00:00 CST
        long actualTimestamp = 1726290000;

        // 2. Expected GTFS Time: 23:55:00 (86100 seconds from midnight)
        // The service day starts 9/13 00:00:00. Expected arrival is 9/13 23:55:00.
        String expectedGtfsTime = "23:55:00"; // 86100 seconds

        String cstTimezone = "America/Chicago";

        // Expected Result: Actual (9/14 00:00) - Expected (9/13 23:55) = +300 seconds
        var difference = TransitDateUtil.calculateTimeDifferenceInSeconds(
                expectedGtfsTime,
                actualTimestamp,
                cstTimezone
        );

        assertEquals(300, difference);
    }

    @Test
    void calculateTimeDifferenceInSeconds_earlyBus() {
        //9-13-24 11:55PM
        long dateOld = 1726289700;
        String cstTimezone = "America/Chicago";

        var differenceEarly = TransitDateUtil.calculateTimeDifferenceInSeconds("24:00:00", dateOld, cstTimezone);
        assertEquals(-300, differenceEarly);
    }


    @Test
    void testCalculateTimeDifferenceInSeconds() {
        long difference = calculateTimeDifferenceInSeconds("17:09:26", 1714342166, "America/Chicago");
        assertEquals(0, difference);
        long timeAfter = calculateTimeDifferenceInSeconds("17:08:26", 1714342166, "America/Chicago");
        assertEquals(60, timeAfter);
        long timeBefore = calculateTimeDifferenceInSeconds("17:10:26", 1714342166, "America/Chicago");
        assertEquals(-60, timeBefore);
        long newTZ = calculateTimeDifferenceInSeconds("15:09:26", 1714342166, "America/Los_Angeles");
        assertEquals(0, newTZ);
        long over24Hrs = calculateTimeDifferenceInSeconds("29:26:19", 1714559179, "America/Chicago");
        assertEquals(0, over24Hrs);
        long over24HrsFormatted = calculateTimeDifferenceInSeconds("05:26:19", 1714559179, "America/Chicago");
        assertEquals(0, over24HrsFormatted);
    }
}
