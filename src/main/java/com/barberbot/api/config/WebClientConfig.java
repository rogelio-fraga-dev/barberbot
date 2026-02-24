package com.barberbot.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WebClientConfig {
    
    private final BarberBotProperties properties;
    
    @Bean
    public WebClient webClient() {
        String apiKey = properties.getEvolution().getApiKey();
        return WebClient.builder()
                .baseUrl(properties.getEvolution().getBaseUrl())
                .defaultHeader("apikey", apiKey != null && !apiKey.isEmpty() ? apiKey : "barberbot")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
