package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed;
import com.doug.projects.transitdelayservice.entity.openmobilityfeed.OpenMobilitySource;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class GtfsFeedAggregator {
    @Value("${openMobilityData.feedSource}")
    private String feedUrl;

    private static boolean isTripUpdateFeed(OpenMobilitySource f) {
        return "US".equals(f.getCountryCode()) && "tu".equals(f.getEntityType()) && StringUtils.isBlank(f.getStatus()) && StringUtils.isNotBlank(f.getStaticReference());
    }

    public List<OpenMobilitySource> gatherAllFeedData() {
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
            return routesAttributesIterator.readAll();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gathers all RT feeds and their associated static urls.
     *
     * @return
     */
    public List<AgencyFeed> gatherRTFeeds() {
        List<OpenMobilitySource> allSources = gatherAllFeedData();
        //all OMS, grouped by id
        Map<String, OpenMobilitySource> allSourcesMap = allSources.stream()
                .collect(Collectors.toMap(OpenMobilitySource::getMdbSourceId, Function.identity()));
        //all redirect ids, mapped to their old OMS
        Map<String, OpenMobilitySource> redirectMap = allSources.stream()
                .filter(o -> StringUtils.isNotBlank(o.getRedirectId()))
                .collect(Collectors.toMap(OpenMobilitySource::getRedirectId, Function.identity()));
        //all realTime tripUpdate feeds
        List<OpenMobilitySource> rtFeeds = allSources.stream()
                .filter(GtfsFeedAggregator::isTripUpdateFeed)
                .toList();


        return rtFeeds.stream()
                .map(rtFeed -> {
                    OpenMobilitySource staticFeed = allSourcesMap.get(rtFeed.getStaticReference());

                    return AgencyFeed.builder()
                            .realTimeUrl(rtFeed.getDirectDownloadUrl())
                            .staticUrl(staticFeed.getDirectDownloadUrl())
                            .id(findRootRTFeed(rtFeed.getMdbSourceId(), allSourcesMap, redirectMap))
                            .status(AgencyFeed.Status.ACTIVE.toString())
                            .name(rtFeed.getProvider())
                            .state(rtFeed.getSubdivisionName())
                            .build();
                })
                .toList();
    }

    /**
     * Finds root feed id, searching for redirects in other sources and static references recursively.
     *
     * @param rtFeedId      the current feed id to search for
     * @param allSourcesMap all sources map
     * @param redirectMap   all redirects map
     * @return the root feed id of the current feed id.
     */
    public String findRootRTFeed(String rtFeedId, Map<String, OpenMobilitySource> allSourcesMap, Map<String, OpenMobilitySource> redirectMap) {
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
}
