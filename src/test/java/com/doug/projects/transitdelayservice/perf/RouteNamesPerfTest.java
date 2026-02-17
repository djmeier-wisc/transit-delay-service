package com.doug.projects.transitdelayservice.perf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RouteNamesPerfTest extends PerfTestBase {

    @BeforeEach
    void localSetUp() {
        // create a realistic dataset that previously caused slowness
        createTestData(200, 50, 20); // ~200k delay rows
    }

    @Test
    void measureGetAllRouteNames() throws Exception {
        // warmup
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/getAllRouteNames").param("agencyId", feedId))
                    .andExpect(status().isOk());
        }

        var times = new ArrayList<Long>();
        for (int i = 0; i < 10; i++) {
            long t0 = System.nanoTime();
            mockMvc.perform(get("/v1/getAllRouteNames").param("agencyId", feedId))
                    .andExpect(status().isOk());
            long t1 = System.nanoTime();
            times.add(TimeUnit.NANOSECONDS.toMillis(t1 - t0));
        }

        long avg = (long) times.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println("RouteNamesPerfTest: avg=" + avg + "ms, samples=" + times);

        assertThat(times).isNotEmpty();
    }
}
