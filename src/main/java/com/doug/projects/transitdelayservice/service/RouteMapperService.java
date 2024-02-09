package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.gtfs.csv.RoutesAttributes;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RouteMapperService {
    private static final Map<Integer, String> routeIdToServiceNameMap = new HashMap<>();
    private static final Map<String, String> serviceNameToColorMap = new HashMap<>();
    private static final Map<String, Integer> serviceNameToSortOrderMap = new HashMap<>();
    private static final Map<String, List<Integer>> routeNameToIdsMap = new HashMap<>();

    /**
     * This is a mess, but yml doesn't support maps by default. To be worked on to make this less static
     */
    @PostConstruct
    private void setRouteIdToServiceNameMap() throws RuntimeException {
        try {
            File file = new File("files/routes.csv");
            CsvMapper csvMapper = new CsvMapper();

            CsvSchema schema = CsvSchema.emptySchema()
                    .withHeader();
            MappingIterator<RoutesAttributes> routesAttributesIterator =
                    csvMapper.readerWithSchemaFor(RoutesAttributes.class)
                            .with(schema)
                            .readValues(file);
            List<RoutesAttributes> routesAttributes = routesAttributesIterator.readAll();
            routeIdToServiceNameMap.putAll(routesAttributes.stream()
                    .collect(Collectors.toMap(RoutesAttributes::getRoute_id, RoutesAttributes::getRoute_short_name,
                            (first, second) -> second))); //chose the latter to resolve duplicate key bugs
            serviceNameToColorMap.putAll(routesAttributes.stream()
                    .collect(Collectors.toMap(RoutesAttributes::getRoute_short_name, ra -> "#" +
                            ra.getRoute_color(), (first, second) -> second)));
            serviceNameToSortOrderMap.putAll(routesAttributes.stream()
                    .collect(Collectors.toMap(RoutesAttributes::getRoute_short_name,
                            RoutesAttributes::getRoute_sort_order, (first, second) -> second)));
            routeNameToIdsMap.putAll(routesAttributes.stream()
                    .collect(Collectors.groupingBy(RoutesAttributes::getRoute_short_name,
                            Collectors.mapping(RoutesAttributes::getRoute_id, Collectors.toList()))));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load values into map!", e);
        }
    }

    public String getFriendlyName(Integer routeId) {
        return routeIdToServiceNameMap.getOrDefault(routeId, "UNKNOWN_ROUTE");
    }

    public String getColorFor(String friendlyName) {
        return serviceNameToColorMap.getOrDefault(friendlyName, "#FFFFF");
    }

    public List<String> getAllFriendlyNames() {
        return new ArrayList<>(new HashSet<>(routeIdToServiceNameMap.values()));
    }

    /**
     * Gets the sort order for a given friendly name. Returns -1 if the friendly name is not found.
     *
     * @param routeFriendlyName the friendly name to get the sort order for.
     * @return the sort order for the given friendly name.
     */
    public Integer getSortOrderFor(String routeFriendlyName) {
        return serviceNameToSortOrderMap.getOrDefault(routeFriendlyName, -1);
    }

    public List<Integer> getRouteIdFor(String routeFriendlyName) {
        return routeNameToIdsMap.getOrDefault(routeFriendlyName, Collections.emptyList());
    }
}
