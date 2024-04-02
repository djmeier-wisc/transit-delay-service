package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.service.RouteMapperService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Component
@AllArgsConstructor
public class LineGraphUtil {
    private RouteMapperService routesService;

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

    public LineGraphData getLineGraphData(String routeFriendlyName, List<Double> currData, boolean setColor) {
        LineGraphData lineGraphData = new LineGraphData();
        lineGraphData.setLineLabel(routeFriendlyName);
        lineGraphData.setTension(0.0);
        lineGraphData.setData(currData);
        if (setColor) {
            lineGraphData.setBorderColor(routesService.getColorFor(routeFriendlyName));
        }
        return lineGraphData;
    }

    public void sortByGTFSSortOrder(List<LineGraphData> lineGraphDatas) {
        lineGraphDatas.sort((o1, o2) -> Integer.compare(routesService.getSortOrderFor(o1.getLineLabel()),
                routesService.getSortOrderFor(o2.getLineLabel())));
    }
}
