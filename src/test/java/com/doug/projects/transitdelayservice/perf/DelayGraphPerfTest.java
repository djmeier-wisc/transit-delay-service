package com.doug.projects.transitdelayservice.perf;

import com.doug.projects.transitdelayservice.entity.jpa.*;
import com.doug.projects.transitdelayservice.repository.jpa.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DelayGraphPerfTest extends PerfTestBase {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate ignoredJdbcForInjection; // keep field injection stable

    // use feedId from base class


    // use base class @BeforeEach which creates the feed and cleans the schema
    @BeforeEach
    void localSetUp() {
        // create moderate sized dataset for measurement
        createTestData(200, 50, 20); // ~200k delay rows
    }



    @Test
    void measureGraphAverageEndpoint() throws Exception {
        // pick a subset of routes to query
        List<String> routeNames = IntStream.range(0, 10).mapToObj(i -> "Route-" + i).collect(Collectors.toList());

        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        long end = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);

        // warmup
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/graph/average/" + feedId)
                            .param("startTime", String.valueOf(start))
                            .param("endTime", String.valueOf(end))
                            .param("units", "24")
                            .param("routes", routeNames.toArray(new String[0])))
                    .andExpect(status().isOk());
        }

        var times = new ArrayList<Long>();
        for (int i = 0; i < 10; i++) {
            long t0 = System.nanoTime();
            mockMvc.perform(get("/v1/graph/average/" + feedId)
                            .param("startTime", String.valueOf(start))
                            .param("endTime", String.valueOf(end))
                            .param("units", "24")
                            .param("routes", routeNames.toArray(new String[0])))
                    .andExpect(status().isOk());
            long t1 = System.nanoTime();
            times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        }

        long avg = (long) times.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = percentile(times, 95);

        System.out.println("DelayGraphPerfTest: avg=" + avg + "ms p95=" + p95 + "ms, samples=" + times);

        assertThat(times).isNotEmpty();
        // no strict assertion — just ensure it completes and prints timings
    }

    private long percentile(List<Long> values, int pct) {
        var sorted = values.stream().sorted().collect(Collectors.toList());
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }
}
