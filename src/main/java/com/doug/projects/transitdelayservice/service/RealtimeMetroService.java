package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class RealtimeMetroService {
    private final WebClient client;
    @Value("${metro.realtime}")
    private String realtimeUrl;
    public RealtimeTransitResponse getCurrentRunData() {
        return client.get().uri(realtimeUrl).retrieve().bodyToMono(RealtimeTransitResponse.class).block();
    }
}
