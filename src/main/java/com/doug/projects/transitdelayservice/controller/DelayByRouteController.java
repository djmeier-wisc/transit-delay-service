package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.LineGraphDataResponse;
import com.doug.projects.transitdelayservice.service.GetDelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

@RequiredArgsConstructor
@Controller("/v1/transit-delay")
@Slf4j
public class DelayByRouteController {
    private final GetDelayService getDelayService;

    @GetMapping("/line/{lineName}")
    public ResponseEntity<LineGraphDataResponse> getLineDelayByLine(@PathVariable String lineName,
                                                                    @RequestParam(required = false) Integer units, @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime) {

        return ResponseEntity.ok(getDelayService.getDelayFor(startTime, endTime, units, lineName));
    }

    @GetMapping("/allLines")
    public ResponseEntity<LineGraphDataResponse> getDelayByAllLines(@RequestParam(required = false) Integer units,
                                                                    @RequestParam(required = false) Long startTime,
                                                                    @RequestParam(required = false) Long endTime) {
        LocalDateTime ldt = LocalDateTime.now();
        if (startTime == null) {
            LocalTime midnight = LocalTime.MIDNIGHT;
            startTime = LocalDateTime.of(ldt.minusDays(7).toLocalDate(), midnight).toEpochSecond(ZoneOffset.of("-5"));
        }
        if (endTime == null) {
            endTime = ldt.toEpochSecond(ZoneOffset.of("-5"));
        }
        if (units == null) {
            units = 7;
        }
        return ResponseEntity.ok(getDelayService.getDelayFor(startTime, endTime, units, null));
    }
}
