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
        //9-13-24 11:55PM
        long dateOld = 1726289700;
        //9-14-24 12:00AM
        long dateNew = 1726290000;
        String cstTimezone = "America/Chicago";
        ZoneId cstZone = ZoneId.of("America/Chicago");
        ZonedDateTime zonedDateOld = ZonedDateTime.of(LocalDateTime.ofInstant(Instant.ofEpochSecond(dateOld), cstZone), cstZone);

        var difference = TransitDateUtil.calculateTimeDifferenceInSeconds(zonedDateOld.toLocalTime().toString(), dateNew, cstTimezone);
        assertEquals(difference, 300);
    }

    @Test
    void calculateTimeDifferenceInSeconds_earlyBus() {
        //9-13-24 11:55PM
        long dateOld = 1726289700;
        //9-14-24 12:00AM
        long dateNew = 1726290000;
        String cstTimezone = "America/Chicago";
        ZoneId cstZone = ZoneId.of("America/Chicago");
        ZonedDateTime zonedDateNew = ZonedDateTime.of(LocalDateTime.ofInstant(Instant.ofEpochSecond(dateNew), cstZone), cstZone);

        var differenceEarly = TransitDateUtil.calculateTimeDifferenceInSeconds(zonedDateNew.toLocalTime().toString(), dateOld, cstTimezone);
        assertEquals(differenceEarly, -300);
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
