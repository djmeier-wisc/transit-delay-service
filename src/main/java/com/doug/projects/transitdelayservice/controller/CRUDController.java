package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CRUDController {
    private final GtfsStaticRepository staticRepository;

    @GetMapping("/v1/getAllRouteNames")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public Mono<List<String>> getAllRoutes(@RequestParam(defaultValue = "394") @Parameter(description = "Agency ID, defaulting to 394/Metro Transit Madison") final String agencyId) {
        return staticRepository.findAllRouteNames(agencyId);
    }
}
