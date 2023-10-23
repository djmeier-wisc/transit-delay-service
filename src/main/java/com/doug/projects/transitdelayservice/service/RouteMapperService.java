package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.gtfs.csv.RoutesAttributes;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RouteMapperService {
    private static final Map<Integer, String> routeIdToServiceNameMap = new HashMap<>();
    private static final Map<String, String> serviceNameToColorMap = new HashMap<>();

    /**
     * This is a mess, but yml doesn't support maps by default. To be worked on to make this less static
     */
    @PostConstruct
    private void setRouteIdToServiceNameMap() throws RuntimeException {
        try {
            File file = ResourceUtils.getFile("classpath:routes.csv");
            CsvMapper csvMapper = new CsvMapper();

            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            MappingIterator<RoutesAttributes> routesAttributesIterator = csvMapper.readerWithSchemaFor(RoutesAttributes.class).with(schema).readValues(file);
            List<RoutesAttributes> routesAttributes = routesAttributesIterator.readAll();
            routeIdToServiceNameMap.putAll(routesAttributes.stream().collect(Collectors.toMap(k -> Integer.parseInt(k.getRoute_id()), RoutesAttributes::getRoute_short_name)));
            serviceNameToColorMap.putAll(routesAttributes.stream().collect(Collectors.toMap(RoutesAttributes::getRoute_short_name, ra -> "#" + ra.getRoute_color())));
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
        return new ArrayList<>(routeIdToServiceNameMap.values());
    }

    /**
     * Refreshes the static maps
     *
     * @return true if successfully write maps, false otherwise
     */
    public boolean refreshMaps() {
        routeIdToServiceNameMap.clear();
        serviceNameToColorMap.clear();
        try {
            setRouteIdToServiceNameMap();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
