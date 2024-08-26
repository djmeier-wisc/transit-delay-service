package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AgenciesController {
    private final AgencyFeedRepository agencyFeedRepository;

    @GetMapping("/v1/agencies/all")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public ResponseEntity<List<AgencyFeed>> getAllAgencies() {
        return ResponseEntity.ok(agencyFeedRepository.getAllAgencyFeeds());
    }

    @GetMapping("/v1/agencies/active")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public Flux<AgencyFeed> getActiveAgencies() {
        return agencyFeedRepository.getFeedFluxByStatus(AgencyFeed.Status.ACTIVE);
    }

    @GetMapping("/v1/agencies/{feedId}")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public Mono<AgencyFeed> getActiveAgencies(@PathVariable String feedId) {
        return agencyFeedRepository.getAgencyFeedById(feedId, false);
    }
}
