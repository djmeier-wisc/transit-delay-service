package com.doug.projects.transitdelayservice.service;

import com.doug.projects.transitdelayservice.entity.gtfs.realtime.RealtimeTransitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;

@Service
@RequiredArgsConstructor
public class RealtimeMetroService {
    private final WebClient client;
    @Value("${metro.realtime}")
    private String realtimeUrl;
    @Value("${metro.gtfsStatic}")
    private String gtfsUrl;
    public RealtimeTransitResponse getCurrentRunData() {
        return client.get().uri(realtimeUrl).retrieve().bodyToMono(RealtimeTransitResponse.class).retry(3).block();
    }

    public BufferedInputStream getGTFSStatic() throws IOException {
        return new BufferedInputStream(new URL(gtfsUrl).openStream());
    }
}
