package com.doug.projects.transitdelayservice.controller;

import com.doug.projects.transitdelayservice.repository.GtfsStaticService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CRUDController {
    private final GtfsStaticService staticRepository;

    @GetMapping("/v1/getAllRouteNames")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
    public ResponseEntity<List<String>> getAllRoutes(@RequestParam(defaultValue = "394") @Parameter(description = "Agency ID, defaulting to 394/Metro Transit Madison") final String agencyId) {
        return ResponseEntity.ok(staticRepository.findAllRouteNamesSorted(agencyId));
    }
}
