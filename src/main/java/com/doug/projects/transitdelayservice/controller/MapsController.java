package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.GtfsShape;
import com.doug.projects.transitdelayservice.entity.MapOptions;
import com.doug.projects.transitdelayservice.service.MapperService;
import lombok.RequiredArgsConstructor;
import org.geojson.FeatureCollection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class MapsController {
    private final MapperService mapperService;

    @GetMapping("/v1/map/{feedId}/delayLines")
    public ResponseEntity<FeatureCollection> getDelayLines(@PathVariable String feedId,
                                                           MapOptions mapOptions) {
        return ResponseEntity.ok(mapperService.getDelayLines(feedId, mapOptions));
    }

    @GetMapping("/v1/map/{feedId}/randomRoute")
    public ResponseEntity<GtfsShape> getRandomRoute(@PathVariable String feedId) {
        return ResponseEntity.ok(mapperService.getRandomGtfsShape(feedId));
    }
}
