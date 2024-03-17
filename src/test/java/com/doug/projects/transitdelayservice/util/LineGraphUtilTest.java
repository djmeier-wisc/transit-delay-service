package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.LineGraphData;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class LineGraphUtilTest {

    @Mock
    private GtfsStaticRepository repository;

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
    void testGetLineGraphDataColorTrue() {
        String routeFriendlyName = "TestRoute";
        String feedId = "TestFeed";
        List<Double> currData = Arrays.asList(1.0, 2.0, 3.0);

        // Mocking the behavior of the repository
        when(repository.getColorFor(feedId, routeFriendlyName)).thenReturn(Optional.of("#FFFFFF"));

        LineGraphData result = lineGraphUtil.getLineGraphData(feedId, routeFriendlyName, currData, true);

        assertEquals(routeFriendlyName, result.getLineLabel());
        assertEquals("#FFFFFF", result.getBorderColor());

        // Verify that getColorFor was called with the correct argument
        verify(repository, times(1)).getColorFor(feedId, routeFriendlyName);
    }

    @Test
    void testGetLineGraphDataColorFalse() {
        String routeFriendlyName = "TestRoute";
        String feedId = "TestFeed";
        List<Double> currData = Arrays.asList(1.0, 2.0, 3.0);

        // Mocking the behavior of the repository
        when(repository.getColorFor(feedId, routeFriendlyName)).thenReturn(Optional.of("#FFFFFF"));

        LineGraphData result = lineGraphUtil.getLineGraphData(feedId, routeFriendlyName, currData, false);

        assertEquals(routeFriendlyName, result.getLineLabel());
        assertNull(result.getBorderColor()); //should not be passed when false

        // Verify that getColorFor was called with the correct argument
        verify(repository, times(0)).getColorFor(feedId, routeFriendlyName);
    }

    @Test
    void testSortByGTFSSortOrder() {
        String feedId = "TestFeed";
        List<LineGraphData> lineGraphDatas =
                Arrays.asList(createLineGraphData("Route1"), createLineGraphData("Route3"), createLineGraphData(
                        "Route2"));//out of order, route3 should be 3rd

        // Mocking the behavior of the repository
        when(repository.getSortOrderFor(feedId, "Route1")).thenReturn(Optional.of(1));
        when(repository.getSortOrderFor(feedId, "Route2")).thenReturn(Optional.of(2));
        when(repository.getSortOrderFor(feedId, "Route3")).thenReturn(Optional.of(3));

        lineGraphUtil.sortByGTFSSortOrder(feedId, lineGraphDatas);

        // Verify that the sorting is done based on the GTFSSortOrder
        assertEquals("Route1", lineGraphDatas.get(0).getLineLabel());
        assertEquals("Route2", lineGraphDatas.get(1).getLineLabel());
        assertEquals("Route3", lineGraphDatas.get(2).getLineLabel());

        // Verify that getSortOrderFor was called for each lineGraphData
        verify(repository, atLeast(1)).getSortOrderFor(feedId, "Route1");
        verify(repository, atLeast(1)).getSortOrderFor(feedId, "Route2");
        verify(repository, atLeast(1)).getSortOrderFor(feedId, "Route3");
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
