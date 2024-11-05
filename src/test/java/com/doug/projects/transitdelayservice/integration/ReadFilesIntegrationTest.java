package com.doug.projects.transitdelayservice.integration;

import com.doug.projects.transitdelayservice.repository.AgencyFeedRepository;
import com.doug.projects.transitdelayservice.repository.AgencyRouteTimestampRepository;
import com.doug.projects.transitdelayservice.repository.GtfsStaticRepository;
import com.doug.projects.transitdelayservice.service.GtfsStaticParserService;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.doug.projects.transitdelayservice.entity.dynamodb.AgencyFeed.ID_INDEX;
import static com.doug.projects.transitdelayservice.entity.dynamodb.GtfsStaticData.AGENCY_TYPE_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest
@NoArgsConstructor
public class ReadFilesIntegrationTest {
    @Autowired
    private GtfsStaticParserService staticService;
    @Autowired
    private GtfsStaticRepository staticRepository;
    @Autowired
    private AgencyFeedRepository feedRepository;
    @Autowired
    private AgencyRouteTimestampRepository timestampRepository;

    private static @NotNull CreateTableEnhancedRequest getCreateTableEnhancedRequest(String indexName) {
        EnhancedGlobalSecondaryIndex idIndex = EnhancedGlobalSecondaryIndex.builder()
                .indexName(indexName)
                .projection(Projection.builder()
                        .projectionType(ProjectionType.ALL)
                        .build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(10000L)
                        .writeCapacityUnits(100000L)
                        .build())
                .build();

        // Create table with secondary index configuration
        CreateTableEnhancedRequest createAgencyFeedTableRequest = CreateTableEnhancedRequest.builder()
                .globalSecondaryIndices(idIndex)
                .build();
        return createAgencyFeedTableRequest;
    }

    @BeforeEach
    @SneakyThrows
    public void createTables() {

        CompletableFuture.allOf(
                staticRepository.createTable(getCreateTableEnhancedRequest(AGENCY_TYPE_INDEX)),
                feedRepository.createTable(getCreateTableEnhancedRequest(ID_INDEX)),
                timestampRepository.createTable()
        ).get();
    }

    @Test
    public void testFileRead() {
        var baseDir = getClass().getResource("/mmt_gtfs").getPath();
        staticService.writeGtfsStaticDataToDynamoFromDiskSync(baseDir, "394");
        StepVerifier.create(staticRepository.findAllRouteNames("394"))
                .expectNext(List.of("A", "B", "C", "D", "E", "F", "G", "H", "J", "L", "O", "P", "R", "S",
                        "W", "28", "38", "55", "65", "75", "80", "81", "82", "84", "60", "61", "62", "63", "64"))
                .verifyComplete();
        var result = staticRepository.findAllStopTimes("394", List.of("4784130")).collectList().block();
        assertEquals(1, result.size());
        assertEquals("A", result.get(0).getRouteName());
        assertEquals("4784130", result.get(0).getId());
        assertEquals(36, result.get(0).getSequencedData().size());
        var routeResult = staticRepository.findAllRoutes("394")
                .filter(r -> "A".equalsIgnoreCase(r.getRouteName()))
                .next()
                .block();
        assertNotNull(routeResult.getRouteColor());
    }
}
