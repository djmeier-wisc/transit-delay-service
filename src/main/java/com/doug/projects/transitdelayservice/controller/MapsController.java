package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.service.MapperService;
import lombok.RequiredArgsConstructor;
import org.geojson.FeatureCollection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class MapsController {
    private final MapperService mapperService;

    @GetMapping("/v1/map/{feedId}/delayPoints")
    public Mono<FeatureCollection> getDelayPoints(@PathVariable String feedId, @RequestParam String routeName) {
        return mapperService.getDelayStopPoints(feedId, routeName);
    }

    @GetMapping("/v1/map/{feedId}/delayLines")
    public Mono<FeatureCollection> getDelayLines(@PathVariable String feedId, @RequestParam String routeName) {
        return mapperService.getDelayLines(feedId, routeName);
    }
}
