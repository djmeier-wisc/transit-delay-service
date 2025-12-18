package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.GraphOptions;
import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.service.GetDelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RequiredArgsConstructor
@RestController
@Slf4j
public class DelayByRouteController {
    private final GetDelayService getDelayService;
    @Value("${metro.feedId}")
    private String metroMadisonFeedId;

    @GetMapping("/v1/average/allLines")
    public ResponseEntity<LineGraphDataResponse> getDelayByAllLines(GraphOptions graphOptions) {
        return ResponseEntity.ok(getDelayService.getAverageDelay(metroMadisonFeedId, graphOptions));
    }

    @GetMapping("/v1/graph/{type}/{feedId}")
    public ResponseEntity<LineGraphDataResponse> getDelayByFeed(@PathVariable String feedId,
                                                                @PathVariable String type,
                                                                GraphOptions graphOptions) {
        return switch (type) {
            case "average" -> ResponseEntity.ok(getDelayService.getAverageDelay(feedId, graphOptions));
            case "max" -> ResponseEntity.ok(getDelayService.getMaxDelayFor(feedId, graphOptions));
            case "percent" -> ResponseEntity.ok(getDelayService.getPercentOnTimeFor(feedId, graphOptions));
            case "median" -> ResponseEntity.ok(getDelayService.getMedianDelay(feedId, graphOptions));
            default -> new ResponseEntity<>(NOT_FOUND);
        };
    }

    @GetMapping("/v1/max/allLines")
    public ResponseEntity<LineGraphDataResponse> getAverageDelayByAllLines(GraphOptions graphOptions) {
        return ResponseEntity.ok(getDelayService.getMaxDelayFor(metroMadisonFeedId, graphOptions));
    }

    @GetMapping("/v1/percent/allLines")
    public ResponseEntity<LineGraphDataResponse> getPercentDelayByAllLines(GraphOptions graphOptions) {
        return ResponseEntity.ok(getDelayService.getPercentOnTimeFor(metroMadisonFeedId, graphOptions));
    }
}
