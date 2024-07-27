package com.doug.projects.transitdelayservice.util;

import com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData;
import org.geojson.LngLatAlt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.TYPE.SHAPE;
import static com.doug.projects.transitdelayservice.service.MapperService.divideShape;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MapperServiceTest {

    @Test
    void testDivideShape() {
        List<GtfsStaticData> gtfsStaticData = List.of(
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.0).stopLon(-122.0).id(":1").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.1).stopLon(-122.1).id(":2").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.2).stopLon(-122.2).id(":3").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.3).stopLon(-122.3).id(":4").build()
        );

        LngLatAlt from = new LngLatAlt();
        from.setLatitude(37.05);
        from.setLongitude(-122.05);

        LngLatAlt to = new LngLatAlt();
        to.setLatitude(37.25);
        to.setLongitude(-122.25);

        List<LngLatAlt> segment = divideShape(gtfsStaticData, from, to);

        assertEquals(3, segment.size());
        assertEquals(37.0, segment.get(0).getLatitude(), 0.0001);
        assertEquals(-122.0, segment.get(0).getLongitude(), 0.0001);
        assertEquals(37.1, segment.get(1).getLatitude(), 0.0001);
        assertEquals(-122.1, segment.get(1).getLongitude(), 0.0001);
        assertEquals(37.2, segment.get(2).getLatitude(), 0.0001);
        assertEquals(-122.2, segment.get(2).getLongitude(), 0.0001);
    }

    @Test
    void testDivideShapeWithReverseOrder() {
        List<GtfsStaticData> gtfsStaticData = List.of(
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.0).stopLon(-122.0).id(":1").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.1).stopLon(-122.1).id(":2").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.2).stopLon(-122.2).id(":3").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.3).stopLon(-122.3).id(":4").build()
        );

        LngLatAlt from = new LngLatAlt();
        from.setLatitude(37.25);
        from.setLongitude(-122.25);

        LngLatAlt to = new LngLatAlt();
        to.setLatitude(37.05);
        to.setLongitude(-122.05);

        List<LngLatAlt> segment = divideShape(gtfsStaticData, from, to);

        assertEquals(3, segment.size());
        assertEquals(37.0, segment.get(0).getLatitude(), 0.0001);
        assertEquals(-122.0, segment.get(0).getLongitude(), 0.0001);
        assertEquals(37.1, segment.get(1).getLatitude(), 0.0001);
        assertEquals(-122.1, segment.get(1).getLongitude(), 0.0001);
        assertEquals(37.2, segment.get(2).getLatitude(), 0.0001);
        assertEquals(-122.2, segment.get(2).getLongitude(), 0.0001);
    }

    @Test
    void testDivideShapeWithNoMatch() {
        List<GtfsStaticData> gtfsStaticData = List.of(
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.0).stopLon(-122.0).id(":1").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.1).stopLon(-122.1).id(":2").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.2).stopLon(-122.2).id(":3").build(),
                GtfsStaticData.builder().agencyType("1:" + SHAPE).shapeId("1").stopLat(37.3).stopLon(-122.3).id(":4").build()
        );

        LngLatAlt from = new LngLatAlt();
        from.setLatitude(36.0);
        from.setLongitude(-121.0);

        LngLatAlt to = new LngLatAlt();
        to.setLatitude(36.5);
        to.setLongitude(-121.5);

        List<LngLatAlt> segment = divideShape(gtfsStaticData, from, to);

        assertEquals(0, segment.size());
    }
}
