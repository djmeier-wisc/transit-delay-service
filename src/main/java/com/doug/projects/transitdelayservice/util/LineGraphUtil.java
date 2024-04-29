package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;

import static java.util.Comparator.*;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

@Component
@RequiredArgsConstructor
public class LineGraphUtil {
    private final GtfsStaticRepository gtfsStaticRepository;

    public static List<String> getColumnLabels(Long startTime, Long endTime, Integer units) {
        double perUnitSecondLength = (double) (endTime - startTime) / units;
        List<String> columnLabels = new ArrayList<>(units);
        for (int currUnit = 0; currUnit < units; currUnit++) {
            final long finalCurrStartTime = (long) (startTime + (perUnitSecondLength * currUnit));
            final long dayInSeconds = 24 * 60 * 60; //the number of seconds in a day
            SimpleDateFormat dateFormat;
            if (perUnitSecondLength >=
                    dayInSeconds) {// if unit length we are going for is a day, set label to date format
                dateFormat = new SimpleDateFormat("MM/dd/yy");
            } else {//if unit length is < a day, measure distance in HH:MM format
                dateFormat = new SimpleDateFormat("MMM/dd/yy hh:mm:ss aa");
            }
            dateFormat.setTimeZone(TimeZone.getTimeZone(ZoneId.of("America/Chicago")));
            columnLabels.add(dateFormat.format(new Date(finalCurrStartTime * 1000)));
        }
        return columnLabels;
    }

    public LineGraphData getLineGraphData(String routeFriendlyName, List<Double> currData) {
        LineGraphData lineGraphData = new LineGraphData();
        lineGraphData.setLineLabel(routeFriendlyName);
        lineGraphData.setTension(0.0);
        lineGraphData.setData(currData);
        return lineGraphData;
    }

    /**
     * Parses the lineLabel as an int as much as possible (as many chars are numerical are treated as a number.)
     * IE O, OA, 1, 1B
     *
     * @param string the lineGraphLabel to parse.
     * @return the number of chars that were numbers, or 0
     */
    public static int parseFirstPartInt(String string) {
        int index = 0;
        while (index < string.length() && Character.isDigit(string.charAt(index))) {
            index++;
        }
        if (index == 0) {
            return -1;
        }
        // Parse the first part of the string as a number.
        return toInt(string.substring(0, index));
    }

    /**
     * Parses the lineLabel as an int as much as possible from the end (as many chars are numerical are treated as a number.)
     * IE B1, B2, B4
     *
     * @param string the lineGraphLabel to parse.
     * @return the number of chars that were numbers, or 0
     */
    public static int parseLastPartInt(String string) {
        int index = string.length() - 1;
        while (index > 0 && Character.isDigit(string.charAt(index))) {
            index--;
        }
        // Parse the first part of the string as a number.
        return toInt(string.substring(index, string.length() - 1));
    }

    public void sortByGTFSSortOrder(String feedId, List<LineGraphData> lineGraphDataList) {
        Map<String, Integer> sortOrderMap = gtfsStaticRepository.getRouteNameToSortOrderMap(feedId)
                .join();
        lineGraphDataList.sort(
                comparing((LineGraphData data) -> sortOrderMap.get(data.getLineLabel()), nullsLast(naturalOrder()))
                        .thenComparing((LineGraphData d) -> parseFirstPartInt(d.getLineLabel()), nullsLast(naturalOrder()))
                        .thenComparing((LineGraphData d) -> parseLastPartInt(d.getLineLabel()), nullsLast(naturalOrder()))
                        .thenComparing(LineGraphData::getLineLabel, nullsLast(naturalOrder()))
        );
    }

    public void populateColor(String feedId, List<LineGraphData> lineGraphDataList) {
        var colorMap = gtfsStaticRepository.getRouteNameToColorMap(feedId).join();
        lineGraphDataList.forEach(lineGraphData -> {
            var color = colorMap.get(lineGraphData.getLineLabel());
            //by accident, if color is not provided in gtfs file, we put #null in the db. Whoops. This fixes that
            if ("#null".equalsIgnoreCase(color) || "#".equalsIgnoreCase(color)) {
                color = null;
            }
            lineGraphData.setBorderColor(color);
        });
    }
}
