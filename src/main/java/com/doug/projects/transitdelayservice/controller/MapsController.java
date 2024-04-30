package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.service.MapperService;
import lombok.RequiredArgsConstructor;
import org.geojson.Feature;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
public class MapsController {
    private final MapperService mapperService;

    @GetMapping("/v1/map/delayPoints/{feedId}")
    public Flux<Feature> getDelayPoints(@PathVariable String feedId, @RequestParam String routeName) {
        return mapperService.getDelayStopPoints(feedId, routeName);
    }
}
