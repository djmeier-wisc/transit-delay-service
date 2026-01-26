package com.doug.projects.transitdelayservice.perf;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.doug.projects.transitdelayservice.repository.jpa.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class PerfTestBase {

    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("testcontainers/init-postgres.sql");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        boolean dockerAvailable;
        try {
            dockerAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }

        if (dockerAvailable) {
            // start container manually (only when Docker is available)
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        } else {
            // fallback to in-memory H2 for environments without Docker (local dev or CI without Docker)
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS MPT");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        }

        // disable background jobs in tests
        registry.add("doesAgencyCronRun", () -> "false");
        registry.add("doesRealtimeCronRun", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected AgencyFeedRepository agencyFeedRepository;

    @Autowired
    protected AgencyRouteRepository agencyRouteRepository;

    @Autowired
    protected AgencyTripRepository agencyTripRepository;

    @Autowired
    protected AgencyTripDelayRepository agencyTripDelayRepository;

    @Autowired
    protected com.doug.projects.transitdelayservice.repository.jpa.AgencyStopRepository agencyStopRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected final String feedId = "TEST_FEED";

    @BeforeEach
    void baseSetUp() {
        // ensure clean schema state (delete children first)
        jdbcTemplate.execute("DELETE FROM MPT.GTFS_TRIP_DELAY");
        jdbcTemplate.execute("DELETE FROM MPT.GTFS_STOP");
        jdbcTemplate.execute("DELETE FROM MPT.GTFS_TRIP");
        jdbcTemplate.execute("DELETE FROM MPT.GTFS_ROUTE");
        jdbcTemplate.execute("DELETE FROM MPT.AGENCY_FEED");

        // insert the test feed entry
        com.doug.projects.transitdelayservice.entity.Status status = com.doug.projects.transitdelayservice.entity.Status.ACTIVE;
        var af = new com.doug.projects.transitdelayservice.entity.jpa.AgencyFeed();
        af.setId(feedId);
        af.setName("Perf Feed");
        af.setState("TS");
        af.setStatus(status);
        agencyFeedRepository.save(af);
    }

    protected void createTestData(int numRoutes, int tripsPerRoute, int delaysPerTrip) {
        var routes = new java.util.ArrayList<com.doug.projects.transitdelayservice.entity.jpa.AgencyRoute>();
        for (int r = 0; r < numRoutes; r++) {
            var routeId = "route-" + r;
            var route = com.doug.projects.transitdelayservice.entity.jpa.AgencyRoute.builder()
                    .id(new com.doug.projects.transitdelayservice.entity.jpa.AgencyRouteId(routeId, feedId))
                    .routeName("Route-" + r)
                    .routeColor("000000")
                    .routeSortOrder(r)
                    .build();
            routes.add(route);
        }
        agencyRouteRepository.saveAll(routes);

        var trips = new java.util.ArrayList<com.doug.projects.transitdelayservice.entity.jpa.AgencyTrip>();
        for (com.doug.projects.transitdelayservice.entity.jpa.AgencyRoute route : routes) {
            for (int t = 0; t < tripsPerRoute; t++) {
                var tripId = route.getRouteId() + "-t-" + t;
                var trip = com.doug.projects.transitdelayservice.entity.jpa.AgencyTrip.builder()
                        .id(new com.doug.projects.transitdelayservice.entity.jpa.AgencyTripId(tripId, feedId))
                        .routeId(route.getRouteId())
                        .shapeId(null)
                        .build();
                trips.add(trip);
            }
        }
        agencyTripRepository.saveAll(trips);

        // create stops to satisfy FK constraint
        var stops = new java.util.ArrayList<com.doug.projects.transitdelayservice.entity.jpa.AgencyStop>();
        for (int s = 0; s < 10; s++) {
            var stop = com.doug.projects.transitdelayservice.entity.jpa.AgencyStop.builder()
                    .id(new com.doug.projects.transitdelayservice.entity.jpa.AgencyStopId("stop-" + s, feedId))
                    .stopName("Stop " + s)
                    .stopLat(0.0)
                    .stopLon(0.0)
                    .build();
            stops.add(stop);
        }
        agencyStopRepository.saveAll(stops);

        // create delays in batches
        var delays = new java.util.ArrayList<com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelay>();
        long timestampBase = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        for (com.doug.projects.transitdelayservice.entity.jpa.AgencyTrip trip : trips) {
            for (int d = 0; d < delaysPerTrip; d++) {
                var delay = com.doug.projects.transitdelayservice.entity.jpa.AgencyTripDelay.builder()
                        .tripId(trip.getTripId())
                        .agencyId(feedId)
                        .timestamp(timestampBase - (d * 60L))
                        .stopId("stop-" + (d % 10))
                        .delaySeconds((d % 10) * 10)
                        .build();
                delays.add(delay);
                if (delays.size() >= 2000) { // save in chunks
                    agencyTripDelayRepository.saveAll(delays);
                    delays.clear();
                }
            }
        }
        if (!delays.isEmpty()) agencyTripDelayRepository.saveAll(delays);
    }
}