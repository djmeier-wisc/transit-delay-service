package com.doug.projects.transitdelayservice;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import com.doug.projects.transitdelayservice.entity.dynamodb.StopTime;
import com.doug.projects.transitdelayservice.entity.dynamodb.Trip;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@SpringBootApplication
@EnableScheduling
@EnableWebFlux
@OpenAPIDefinition(
        servers = {
                @Server(url = "https://api.my-precious-time.com", description = "Production API")
        }
)
public class TransitDelayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransitDelayServiceApplication.class, args);
    }

    @Bean
    public DynamoDbEnhancedClient provideDynamoDb() {
        return DynamoDbEnhancedClient.create();
    }

    @Bean
    public DynamoDbTable<RouteTimestamp> provideTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("dev_msn_route_timestamp", TableSchema.fromBean(RouteTimestamp.class));
    }

    @Bean
    public DynamoDbTable<StopTime> provideStopTimesTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("mmt-stop-times", TableSchema.fromBean(StopTime.class));
    }

    @Bean
    public DynamoDbTable<Trip> provideTripTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("mmt-trips", TableSchema.fromBean(Trip.class));
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
