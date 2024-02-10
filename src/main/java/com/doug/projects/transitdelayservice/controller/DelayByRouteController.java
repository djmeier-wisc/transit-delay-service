package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.GraphOptions;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.service.DelayGraphingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@Slf4j
public class DelayByRouteController {
    private final DelayGraphingService delayGraphingService;
    @GetMapping("/v1/average/allLines")
    public ResponseEntity<LineGraphDataResponse> getDelayByAllLines(GraphOptions graphOptions) {
        return ResponseEntity.ok(delayGraphingService.getAverageDelay(graphOptions));
    }

    @GetMapping("/v1/max/allLines")
    public ResponseEntity<LineGraphDataResponse> getAverageDelayByAllLines(GraphOptions graphOptions) {
        return ResponseEntity.ok(delayGraphingService.getMaxDelayFor(graphOptions));
    }

    @GetMapping("/v1/percent/allLines")
    public ResponseEntity<LineGraphDataResponse> getPercentDelayByAllLines(GraphOptions graphOptions, @RequestParam(defaultValue = "-5") Integer lowerOnTimeThreshold, @RequestParam(defaultValue = "5") Integer upperOnTimeThreshold) {
        return ResponseEntity.ok(delayGraphingService.getPercentOnTimeFor(graphOptions, lowerOnTimeThreshold, upperOnTimeThreshold));
    }
}
