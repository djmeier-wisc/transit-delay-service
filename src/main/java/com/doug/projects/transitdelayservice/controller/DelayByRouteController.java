package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.service.GetDelayService;
import com.doug.projects.transitdelayservice.service.GetOnTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Controller(value = "/graph")
@CrossOrigin(origins = {"http://localhost:3000", "www.my-precious-time.com"})
@Slf4j
public class DelayByRouteController {
    private final GetDelayService getDelayService;
    private final GetOnTimeService getOnTimeService;

    @GetMapping("/v1/average/allLines")
    public ResponseEntity<LineGraphDataResponse> getDelayByAllLines(@RequestParam(required = false) Integer units, @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime, @RequestParam(required = false) List<String> routes) {
        return ResponseEntity.ok(getDelayService.getAverageDelay(startTime, endTime, units, routes));
    }

    @GetMapping("/v1/max/allLines")
    public ResponseEntity<LineGraphDataResponse> getAverageDelayByAllLines(@RequestParam(required = false) Integer units, @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime, @RequestParam(required = false) List<String> routes) {
        return ResponseEntity.ok(getDelayService.getMaxDelayFor(startTime, endTime, units, routes));
    }

    @GetMapping("/v1/percent/allLines")
    public ResponseEntity<LineGraphDataResponse> getPercentDelayByAllLines(@RequestParam(required = false) Integer units, @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime, @RequestParam(required = false, defaultValue = "5") Integer onTimeDifferenceDefinition, @RequestParam(required = false) List<String> routes) {
        return ResponseEntity.ok(getOnTimeService.getPercentOnTimeFor(startTime, endTime, units, routes,
                onTimeDifferenceDefinition));
    }
}
