package com.doug.projects.transitdelayservice.perf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DelayLinesPerfTest extends PerfTestBase {

    // use feedId and helpers from base class


    @BeforeEach
    void localSetUp() {
        // rely on base class cleanup/insert; create dataset for this test
        createTestData(200, 50, 20); // ~200k delay rows
    }


    @Test
    void measureDelayLinesEndpoint() throws Exception {
        // pick some route names
        List<String> routeNames = IntStream.range(0, 20).mapToObj(i -> "Route-" + i).collect(Collectors.toList());

        // warmup
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/map/" + feedId + "/delayLines")
                            .param("routeNames", routeNames.toArray(new String[0])))
                    .andExpect(status().isOk());
        }

        var times = new ArrayList<Long>();
        for (int i = 0; i < 10; i++) {
            long t0 = System.nanoTime();
            mockMvc.perform(get("/v1/map/" + feedId + "/delayLines")
                            .param("routeNames", routeNames.toArray(new String[0])))
                    .andExpect(status().isOk());
            long t1 = System.nanoTime();
            times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        }

        long avg = (long) times.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = percentile(times, 95);

        System.out.println("DelayLinesPerfTest: avg=" + avg + "ms p95=" + p95 + "ms, samples=" + times);

        assertThat(times).isNotEmpty();
    }

    private long percentile(List<Long> values, int pct) {
        var sorted = values.stream().sorted().collect(Collectors.toList());
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }
}
