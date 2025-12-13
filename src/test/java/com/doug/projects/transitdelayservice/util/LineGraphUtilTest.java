package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.repository.GtfsStaticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LineGraphUtilTest {

    @Mock
    private GtfsStaticService repository;

    @InjectMocks
    private LineGraphUtil lineGraphUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetColumnLabels() {
        long startTime = 0L;
        long endTime = 100L;
        int units = 5;

        List<String> result = LineGraphUtil.getColumnLabels(startTime, endTime, units);

        // You can add assertions based on your expected results
        // For simplicity, let's just check the size of the result list
        assertEquals(units, result.size());
    }
    @Test
    void testPopulateColor() {
        var feedId = "TestFeed";
        var lineGraphData = List.of(createLineGraphData("r1"), createLineGraphData("r2"));
        when(repository.getRouteNameToColorMap(feedId)).thenReturn(
                        Map.of("r1", "#FFF111", "r2", "#FFF222")
        );
        lineGraphUtil.populateColor(feedId, lineGraphData);
        assertEquals("#FFF111", lineGraphData.get(0).getBorderColor());
        assertEquals("#FFF222", lineGraphData.get(1).getBorderColor());
    }
    @Test
    void testSortByGTFSSortOrder() {
        String feedId = "TestFeed";
        List<LineGraphData> lineGraphDatas =
                Arrays.asList(createLineGraphData("Route1"), createLineGraphData("Route3"), createLineGraphData(
                        "Route2"));//out of order, route3 should be 3rd

        // Mocking the behavior of the repository
        when(repository.getRouteNameToSortOrderMap(feedId)).thenReturn(Map.of("Route1", 1, "Route2", 2, "Route3", 3));

        lineGraphUtil.sortByGTFSSortOrder(feedId, lineGraphDatas);

        // Verify that the sorting is done based on the GTFSSortOrder
        assertEquals("Route1", lineGraphDatas.get(0).getLineLabel());
        assertEquals("Route2", lineGraphDatas.get(1).getLineLabel());
        assertEquals("Route3", lineGraphDatas.get(2).getLineLabel());

        // Verify that getSortOrderFor was called for each lineGraphData
        verify(repository, times(1)).getRouteNameToSortOrderMap(feedId);
    }

    private LineGraphData createLineGraphData(String label) {
        LineGraphData lineGraphData = new LineGraphData();
        lineGraphData.setLineLabel(label);
        lineGraphData.setTension(0.0);
        lineGraphData.setData(Arrays.asList(1.0, 2.0, 3.0));
        lineGraphData.setBorderColor("#FFFFFF");
        return lineGraphData;
    }
}
