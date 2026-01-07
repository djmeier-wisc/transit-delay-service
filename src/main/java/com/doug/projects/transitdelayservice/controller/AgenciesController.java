package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.Status;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.service.AgencyFeedService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AgenciesController {
    private final AgencyFeedService agencyFeedService;

    @GetMapping("/v1/agencies/all")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public ResponseEntity<List<AgencyFeedDto>> getAllAgencies() {
        return ResponseEntity.ok(agencyFeedService.getAllAgencyFeeds());
    }

    @GetMapping("/v1/agencies/active")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public ResponseEntity<List<AgencyFeedDto>> getActiveAgencies() {
        return ResponseEntity.ok(agencyFeedService.getAgencyFeedsByStatus(Status.ACTIVE));
    }

    @GetMapping("/v1/agencies/{feedId}")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public ResponseEntity<AgencyFeedDto> getActiveAgencies(@PathVariable String feedId) {
        return agencyFeedService.getAgencyFeedById(feedId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/v1/agencies/{feedId}/status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public ResponseEntity<String> getActiveAgenciesStatus(@PathVariable String feedId) {
        return agencyFeedService.getAgencyFeedStatusById(feedId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
