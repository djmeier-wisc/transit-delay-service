package com.doug.projects.transitdelayservice.repository;

import com.doug.projects.transitdelayservice.entity.jpa.AgencyRoute;
import com.doug.projects.transitdelayservice.repository.jpa.AgencyRouteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class GtfsStaticService {

    private final AgencyRouteRepository agencyRouteRepository;

    public GtfsStaticService(AgencyRouteRepository agencyRouteRepository) {
        this.agencyRouteRepository = agencyRouteRepository;
    }

    /**
     * Finds all route names for a particular agencyId.
     * Note that this can either be routeShortName or routeLongName, depending on which was specified in routes.txt file.
     * Sorts by routeSortOrder, otherwise, sorts by
     *
     * @param agencyId the agencyId to search the DB for
     * @return the routeNames associated with that agency.
     */
    public List<String> findAllRouteNamesSorted(String agencyId) {
        return agencyRouteRepository.findAllByAgencyIdFiltered(agencyId);
    }

    public Map<String, String> getRouteNameToColorMap(String agencyId) {
        return agencyRouteRepository.findAllByAgency_Id(agencyId).stream()
                .filter(route -> route != null && route.getRouteName() != null && route.getRouteColor() != null)
                .collect(Collectors.toMap(AgencyRoute::getRouteName, AgencyRoute::getRouteColor, (first, second) -> second));
    }

    public Map<String, Integer> getRouteNameToSortOrderMap(String agencyId) {
        return agencyRouteRepository.findAllByAgency_Id(agencyId).stream()
                .filter(route -> route != null && route.getRouteName() != null && route.getRouteSortOrder() != null)
                .collect(Collectors.toMap(AgencyRoute::getRouteName, AgencyRoute::getRouteSortOrder, (first, second) -> second));
    }
}
