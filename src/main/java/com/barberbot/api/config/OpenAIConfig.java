package com.barberbot.api.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class OpenAIConfig {
    
    private final BarberBotProperties properties;
    
    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(properties.getOpenai().getApiKey())
                .modelName(properties.getOpenai().getModel()) // gpt-4o
                .temperature(properties.getOpenai().getTemperature())
                .timeout(Duration.ofSeconds(60)) // Aumentar timeout para imagens
                .build();
    }
}