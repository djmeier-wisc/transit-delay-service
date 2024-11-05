package com.doug.projects.transitdelayservice.integration;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Profile("test")
@Configuration
public class DynamoDbConfiguration {

    private DynamoDBProxyServer dynamoServer;

    @Bean
    @SneakyThrows
    public DynamoDBProxyServer createDynamoServer() {
        dynamoServer = ServerRunner.createServerFromCommandLineArgs(new String[]{"-inMemory", "-port", "8000"});
        dynamoServer.start();
        return dynamoServer;
    }

    @PreDestroy
    @SneakyThrows
    public void destroyDynamoServer() {
        if (dynamoServer != null) {
            dynamoServer.stop();
        }
    }

    @Bean
    public DynamoDbClient provideClient(DynamoDBProxyServer server) {
        System.setProperty("software.amazon.awssdk.http.service.impl", "software.amazon.awssdk.http.urlconnection.UrlConnectionSdkHttpService");
        return DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:8000"))
                .region(Region.US_EAST_1)  // Region is required even for local instances
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient provideDynamoDb(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbEnhancedAsyncClient provideAsyncEnhancedClient() {
        var dynamoDbAsyncClient = DynamoDbAsyncClient.builder()
                .endpointOverride(URI.create("http://localhost:8000"))
                .region(Region.US_EAST_1)
                .build();
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();
    }
}
