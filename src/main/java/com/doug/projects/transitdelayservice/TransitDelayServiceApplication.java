package com.doug.projects.transitdelayservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableScheduling
@EnableWebFlux
@OpenAPIDefinition(
        servers = {
                @Server(url = "https://api.my-precious-time.com", description = "Production API")
        }
)
@RequiredArgsConstructor
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class TransitDelayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransitDelayServiceApplication.class, args);
    }

    @Bean
    @Profile("!test")
    public DynamoDbEnhancedClient provideDynamoDb() {
        return DynamoDbEnhancedClient.create();
    }

    @Bean
    @Profile("!test")
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

    @Bean("realtime")
    public Executor realtimeExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean("retry")
    public Executor retryExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean("dynamoWriting")
    public Executor dynamoExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean
    @Profile("!test")
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.create();
    }

    @Bean
    @Profile("!test")
    public LockProvider lockProvider(DynamoDbClient dynamoDB) {
        return new DynamoDBLockProvider(dynamoDB, "shedLock");
    }
}
