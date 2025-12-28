package com.doug.projects.transitdelayservice;

import com.google.protobuf.ExtensionRegistry;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
        servers = {
                @Server(url = "https://api.my-precious-time.com", description = "Production API"),
                @Server(url = "http://localhost:8080", description = "Local API")
        }
)
@RequiredArgsConstructor
public class TransitDelayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransitDelayServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, ProtobufHttpMessageConverter converter) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5)) // Time to establish connection
                .setReadTimeout(Duration.ofSeconds(5))    // Time to wait for data
                .additionalMessageConverters(converter)
                .build();
    }

    @Bean
    public ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        return new ProtobufHttpMessageConverter();
    }
}
