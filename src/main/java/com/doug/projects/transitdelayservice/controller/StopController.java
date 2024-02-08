package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.service.StopMapperService;
import com.doug.projects.transitdelayservice.service.StopTimeService;
import com.doug.projects.transitdelayservice.service.TripDelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class StopController {
    private final StopMapperService mapperService;
    private final StopTimeService stopTimeService;
    private final TripDelayService tripDelayService;

    @GetMapping("/v1/searchStop/name")
    public ResponseEntity<List<String>> searchStop(@RequestParam String stopName, @RequestParam String route,
                                                   @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseEntity.ofNullable(mapperService.searchStops(stopName, limit).toList());
    }

    @GetMapping("/v1/searchStop/departures")
    public ResponseEntity<List<String>> searchDepartures(@RequestParam String stopName,
                                                         @RequestParam String routeFriendlyName) {
        return ResponseEntity.ofNullable(stopTimeService.getScheduledDepartureTimesForStop(stopName,
                routeFriendlyName));
    }

    @GetMapping("/v1/searchStop/stopDelay")
    public ResponseEntity<Double> getDelayAtStop(@RequestParam String stopName, @RequestParam String route, @RequestParam String time, @RequestParam(defaultValue = "30") Integer searchPeriod) {
        Optional<Double> delay = tripDelayService.getAverageDelayForStop(stopName, route, time, 30);
        if (delay.isEmpty())
            return ResponseEntity.notFound().build();
        else
            return ResponseEntity.ok(delay.get());
    }
}