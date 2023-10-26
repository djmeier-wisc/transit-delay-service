package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.service.GetDelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RequiredArgsConstructor
@Controller(value = "/graph")
@Slf4j
public class DelayByRouteController {
    private final GetDelayService getDelayService;
    private final RouteTimestampRepository repository;

    @GetMapping("/v1/line/{lineName}")
    public ResponseEntity<LineGraphDataResponse> getLineDelayByLine(@PathVariable String lineName,
                                                                    @RequestParam(required = false) Integer units, @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime) {

        return ResponseEntity.ok(getDelayService.getDelayFor(startTime, endTime, units, lineName));
    }

    @GetMapping("/v1/allLines")
    public ResponseEntity<LineGraphDataResponse> getDelayByAllLines(@RequestParam(required = false) Integer units,
                                                                    @RequestParam(required = false) Long startTime,
                                                                    @RequestParam(required = false) Long endTime) {
        return ResponseEntity.ok(getDelayService.getDelayFor(startTime, endTime, units, null));
    }

}
