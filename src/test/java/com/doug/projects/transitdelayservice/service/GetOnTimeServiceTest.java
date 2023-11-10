package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.LineGraphUtil;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class GetOnTimeServiceTest {

    @Mock
    private RouteMapperService routeMapperService;

    @Mock
    private RouteTimestampRepository repository;

    @Mock
    private LineGraphUtil lineGraphUtil;

    @Mock
    private GetDelayService getDelayService;

    @InjectMocks
    private GetOnTimeService getOnTimeService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetPercentOnTimeForWhenStartAndEndNullThenUseDefaultValues() {
        when(repository.getRouteTimestampsMapBy(anyLong(), anyLong())).thenReturn(Collections.emptyMap());
        when(LineGraphUtil.getColumnLabels(anyLong(), anyLong(), anyInt())).thenReturn(Collections.emptyList());

        LineGraphDataResponse response = getOnTimeService.getPercentOnTimeFor(null, null, null, null, 5);

        verify(repository).getRouteTimestampsMapBy(TransitDateUtil.getMidnightSixDaysAgo(),
                TransitDateUtil.getMidnightTonight());
        verify(lineGraphUtil).getColumnLabels(TransitDateUtil.getMidnightSixDaysAgo(),
                TransitDateUtil.getMidnightTonight(), 7);
        assertNotNull(response);
    }

    @Test
    public void testGetPercentOnTimeForWhenStartGreaterOrEqualEndThenThrowException() {
        assertThrows(IllegalArgumentException.class, () -> getOnTimeService.getPercentOnTimeFor(10L, 10L, null, null,
                5));
    }

    @Test
    public void testGetPercentOnTimeForWhenRouteNullThenUseAllRoutes() {
        when(repository.getRouteTimestampsMapBy(anyLong(), anyLong())).thenReturn(Collections.emptyMap());
        when(routeMapperService.getAllFriendlyNames()).thenReturn(Collections.emptyList());

        LineGraphDataResponse response = getOnTimeService.getPercentOnTimeFor(1L, 10L, 5, null, 5);

        verify(repository).getRouteTimestampsMapBy(1L, 10L);
        verify(routeMapperService).getAllFriendlyNames();
        assertNotNull(response);
    }

    @Test
    public void testGetPercentOnTimeForWhenRouteNotNullThenUseSpecificRoute() {
        when(repository.getRouteTimestampsMapBy(anyLong(), anyLong())).thenReturn(Collections.emptyMap());

        LineGraphDataResponse response =
                getOnTimeService.getPercentOnTimeFor(1L, 10L, 5, Collections.singletonList("route"), 5);

        verify(repository).getRouteTimestampsMapBy(1L, 10L);
        assertNotNull(response);
    }
}
