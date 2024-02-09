package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.transit.Stop;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import jakarta.annotation.PostConstruct;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StopMapperService {
    private static final Map<String, List<Stop>> stopMap = new HashMap<>(3000);
    private static Stop[] stopList = new Stop[0];
    private static List<String> stopNames = new ArrayList<>(3000);

    @PostConstruct
    private void dataSetup() throws RuntimeException {
        try {
            File file = new File("files/stops.csv");
            CsvMapper csvMapper = new CsvMapper();

            CsvSchema schema = CsvSchema.emptySchema()
                    .withHeader();

            //note, I am using arrays here. While 3k isn't that much, other systems may have more in the future
            MappingIterator<Stop> routesAttributesIterator = csvMapper.readerWithSchemaFor(Stop.class)
                    .with(schema)
                    .readValues(file);
            stopList = routesAttributesIterator.readAll()
                    .toArray(stopList);
            stopMap.putAll(Stream.of(stopList)
                    .collect(Collectors.groupingBy(Stop::getStop_name)));
            stopNames = Arrays.stream(stopList)
                    .map(Stop::getStop_name)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load values into map!", e);
        }
    }

    public List<Stop> searchForStop(String search, int limit) {
        if (limit <= 0)
            limit = 10;
        return searchStops(search, limit).flatMap(this::getStopsByName)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets a stream of stops by their name
     *
     * @param stopName
     * @return
     */
    public Stream<Stop> getStopsByName(String stopName) {
        return stopMap.getOrDefault(stopName, Collections.emptyList())
                .stream();
    }

    /**
     * @param stopName the stopName to fuzzy search for
     * @param limit
     * @return
     */
    public Stream<String> searchStops(String stopName, int limit) {
        if (limit <= 0)
            limit = 10;
        return searchStops(stopName).limit(limit);
    }

    public Stream<String> searchStops(String stopName) {
        return FuzzySearch.extractSorted(stopName, StopMapperService.stopNames)
                .stream()
                .map(ExtractedResult::getString);
    }
}
