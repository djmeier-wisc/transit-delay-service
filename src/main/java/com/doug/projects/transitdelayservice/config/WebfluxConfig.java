package com.doug.projects.transitdelayservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebfluxConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer codecConfigurer) {
        codecConfigurer.defaultCodecs().maxInMemorySize(1000*1000*1024);
    }
}
