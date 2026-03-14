package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.Status;
import com.doug.projects.transitdelayservice.entity.jpa.AgencyFeedDto;
import com.doug.projects.transitdelayservice.entity.openmobilityfeed.OpenMobilitySource;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.doug.projects.transitdelayservice.config.OpenMobilityDataProperties;

@RequiredArgsConstructor
@Service
@Slf4j
public class GtfsFeedAggregator {
    @Value("${open-mobility-data.feed-source}")
    private String feedUrl;
    @Value("#{'${open-mobility-data.allowed-ids}'.split(',')}")
    private Set<String> allowedFeedIds;

    private final OpenMobilityDataProperties openMobilityDataProperties;

    private static boolean isTripUpdateFeed(OpenMobilitySource f) {
        return "tu".equals(f.getEntityType()) && StringUtils.isNotBlank(f.getStaticReference());
    }

    private static String findNewStaticId(String oldStaticId, Map<String, String> oldIdToNewIdMap) {
        String newStaticId = oldIdToNewIdMap.get(oldStaticId);
        if (newStaticId == null) {
            return oldStaticId;
        }
        return findNewStaticId(newStaticId, oldIdToNewIdMap);
    }

    /**
     * Finds root feed id, searching for redirects in other sources and static references recursively.
     *
     * @param rtFeedId      the current feed id to search for
     * @param allSourcesMap all sources map
     * @param redirectMap   all redirects map
     * @return the root feed id of the current feed id.
     */
    private static String findRootRTFeed(String rtFeedId, Map<String, OpenMobilitySource> allSourcesMap, Map<String,
            OpenMobilitySource> redirectMap) {
        if (redirectMap.containsKey(rtFeedId)) {
            return findRootRTFeed(redirectMap.get(rtFeedId).getMdbSourceId(), allSourcesMap, redirectMap);
        }
        String rtFeedStaticReference = allSourcesMap.getOrDefault(rtFeedId, new OpenMobilitySource())
                .getStaticReference();
        if (StringUtils.isNotBlank(rtFeedStaticReference)) {
            return findRootRTFeed(rtFeedStaticReference, allSourcesMap, redirectMap);
        }
        return rtFeedId;
    }

    private List<OpenMobilitySource> gatherAllFeedData() {
        try {
            BufferedInputStream in = new BufferedInputStream(java.net.URI.create(feedUrl)
                    .toURL()
                    .openStream());
            CsvMapper csvMapper = new CsvMapper();

            CsvSchema schema = CsvSchema.emptySchema()
                    .withHeader();
            MappingIterator<OpenMobilitySource> routesAttributesIterator =
                    csvMapper.readerWithSchemaFor(OpenMobilitySource.class)
                            .with(schema)
                            .readValues(in);
            List<OpenMobilitySource> all = routesAttributesIterator.readAll();
            return all;
        } catch (IOException e) {
            log.error("Failed to read feeds", e);
            return Collections.emptyList();
        }
    }

    /**
     * Gathers all RT feeds and their associated static urls.
     *
     * @return
     */
    public List<AgencyFeedDto> gatherRTFeeds() {
        List<OpenMobilitySource> allSources = gatherAllFeedData();
        //all OMS, grouped by id
        Map<String, OpenMobilitySource> allSourcesMap = allSources.stream()
                .collect(Collectors.toMap(OpenMobilitySource::getMdbSourceId, Function.identity()));
        //all redirect ids, mapped to their old OMS
        Map<String, OpenMobilitySource> redirectMap = allSources.stream()
                .filter(o -> StringUtils.isNotBlank(o.getRedirectId()))
                .collect(Collectors.toMap(OpenMobilitySource::getRedirectId, Function.identity(), (a, b) -> a));
        Map<String, String> oldIdToNewIdMap = allSources.stream()
                .filter(o -> StringUtils.isNotBlank(o.getRedirectId()))
                .collect(Collectors.toMap(OpenMobilitySource::getMdbSourceId, OpenMobilitySource::getRedirectId));
        //all realTime tripUpdate feeds
        List<OpenMobilitySource> rtFeeds = allSources.stream()
                .filter(GtfsFeedAggregator::isTripUpdateFeed)
                .filter(f -> StringUtils.isEmpty(f.getRedirectId()))
                .toList();


        var feedsFromMbd = rtFeeds.stream()
                .filter(f -> allowedFeedIds.contains(findRootRTFeed(f.getMdbSourceId(), allSourcesMap, redirectMap)))
                .map(rtFeed -> {
                    OpenMobilitySource staticFeed =
                            allSourcesMap.get(findNewStaticId(rtFeed.getStaticReference(), oldIdToNewIdMap));
                    return AgencyFeedDto.builder()
                            .realTimeUrl(rtFeed.getDirectDownloadUrl())
                            .staticUrl(staticFeed.getDirectDownloadUrl())
                            .id(findRootRTFeed(rtFeed.getMdbSourceId(), allSourcesMap, redirectMap))
                            .status(Status.ACTIVE)
                            .name(rtFeed.getProvider())
                            .state(rtFeed.getSubdivisionName())
                            .build();
                })
                .sorted(Comparator.comparing(AgencyFeedDto::getId))
                .toList();
        List<AgencyFeedDto> hardcodedFeeds = openMobilityDataProperties.getHardcodedFeeds().stream()
                .map(hf -> AgencyFeedDto.builder()
                        .realTimeUrl(hf.getRtTu())
                        .staticUrl(hf.getStaticUrl())
                        .id("hardcoded-static-" + hf.getStaticUrl())
                        .status(Status.ACTIVE)
                        .name("Hardcoded Feed")
                        .state("N/A")
                        .build())
                .toList();
        return Stream.concat(feedsFromMbd.stream(), hardcodedFeeds.stream())
                .toList();
    }
}
