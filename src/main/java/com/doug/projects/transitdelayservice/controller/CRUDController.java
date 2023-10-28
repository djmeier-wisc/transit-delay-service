package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.repository.RouteTimestampRepository;
import com.doug.projects.transitdelayservice.service.RouteMapperService;
import com.doug.projects.transitdelayservice.util.TransitDateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "www.my-precious-time.com"})
public class CRUDController {
    private final RouteTimestampRepository repository;
    private final RouteMapperService mapperService;

    @GetMapping("/v1/getData")
    public ResponseEntity<List<RouteTimestamp>> getData(@RequestParam(required = false) String route, @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime) {
        if (startTime == null) startTime = TransitDateUtil.getMidnightSixDaysAgo();
        if (endTime == null) endTime = TransitDateUtil.getMidnightTonight();
        if (route == null) return ResponseEntity.ok(repository.getRouteTimestampsBy(startTime, endTime));
        if (startTime <= endTime) return ResponseEntity.badRequest().body(null);
        return ResponseEntity.ok(repository.getRouteTimestampsBy(startTime, endTime, route));
    }

    @GetMapping("/v1/getAllRoutes")
    public ResponseEntity<List<String>> getAllRoutes() {
        return ResponseEntity.ok(mapperService.getAllFriendlyNames().stream().sorted((o1, o2) -> Integer.compare(mapperService.getSortOrderFor(o1), mapperService.getSortOrderFor(o2))).collect(Collectors.toList()));
    }
}
