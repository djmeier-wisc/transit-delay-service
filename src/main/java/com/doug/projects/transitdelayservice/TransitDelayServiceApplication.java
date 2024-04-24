package com.doug.projects.transitdelayservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

@SpringBootApplication
@EnableScheduling
@EnableWebFlux
@OpenAPIDefinition(
        servers = {
                @Server(url = "https://api.my-precious-time.com", description = "Production API")
        }
)
@RequiredArgsConstructor
public class TransitDelayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransitDelayServiceApplication.class, args);
    }

    @Bean
    public DynamoDbEnhancedClient provideDynamoDb() {
        return DynamoDbEnhancedClient.create();
    }

    @Bean
    public DynamoDbEnhancedAsyncClient provideAsyncEnhancedClient() {
        return DynamoDbEnhancedAsyncClient.create();
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(clientCodecConfigurer -> clientCodecConfigurer
                                        .defaultCodecs().maxInMemorySize((1000 * 1000 * 1024))).build())
                .codecs(clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs()
                        .maxInMemorySize(1000 * 1000 * 1024)).build();
    }
}
