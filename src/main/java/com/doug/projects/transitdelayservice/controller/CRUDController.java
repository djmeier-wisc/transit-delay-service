package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CRUDController {
    private final RouteTimestampRepository repository;

    @GetMapping("/v1/getData")
    public ResponseEntity<List<RouteTimestamp>> getData(@RequestParam(required = false) String route,
                                                        @RequestParam(required = false) Long startTime,
                                                        @RequestParam(required = false) Long endTime) {
        if (startTime == null) startTime = TransitDateUtil.getMidnightSixDaysAgo();
        if (endTime == null) endTime = TransitDateUtil.getMidnightTonight();
        if (route == null) return ResponseEntity.ok(repository.getRouteTimestampsBy(startTime, endTime));
        if (startTime <= endTime) return ResponseEntity.badRequest().body(null);
        return ResponseEntity.ok(repository.getRouteTimestampsBy(startTime, endTime, route));
    }
}
