package com.doug.projects.transitdelayservice;

import com.doug.projects.transitdelayservice.entity.DelayObject;
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
public class TransitDelayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransitDelayServiceApplication.class, args);
    }

    @Bean
    public DynamoDbEnhancedClient provideDynamoDb() {
        return DynamoDbEnhancedClient.create();
    }

    @Bean
    public DynamoDbTable<DelayObject> provideTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("transit_delay", TableSchema.fromBean(DelayObject.class));
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
