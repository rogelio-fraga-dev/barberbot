package com.barberbot.api.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OpenAIConfig {
    
    private final BarberBotProperties properties;
    
    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(properties.getOpenai().getApiKey())
                .modelName(properties.getOpenai().getModel())
                .temperature(properties.getOpenai().getTemperature())
                .build();
    }
    
    // TODO: Implementar Vision Model quando disponível na versão do LangChain4j
    // Por enquanto, usar chamadas diretas à API OpenAI para visão
}
