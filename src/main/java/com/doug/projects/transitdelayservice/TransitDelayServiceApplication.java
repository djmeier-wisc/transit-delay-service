package com.doug.projects.transitdelayservice;

import com.doug.projects.transitdelayservice.entity.dynamodb.RouteTimestamp;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
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
@OpenAPIDefinition(info = @Info(
        title = "Transit-Delay-Service",
        version = "1.0",
        description = "Service to measure the average difference from schedule for Metro Transit in Madison, WI"
))
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
    public WebClient webClient() {
        return WebClient.builder()
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(clientCodecConfigurer -> clientCodecConfigurer
                                        .defaultCodecs().maxInMemorySize((1000 * 1000 * 1024))).build())
                .codecs(clientCodecConfigurer -> {
                    clientCodecConfigurer.defaultCodecs().maxInMemorySize(1000 * 1000 * 1024);
        }).build();
    }
}
