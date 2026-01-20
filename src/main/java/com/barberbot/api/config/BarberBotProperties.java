package com.barberbot.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "barberbot")
public class BarberBotProperties {
    private Admin admin = new Admin();
    private Evolution evolution = new Evolution();
    private OpenAI openai = new OpenAI();
    private Schedule schedule = new Schedule();
    
    @Data
    public static class Admin {
        private String phone;
    }
    
    @Data
    public static class Evolution {
        private String baseUrl;
        private String instanceName;
        private String apiKey;
    }
    
    @Data
    public static class OpenAI {
        private String apiKey;
        private String model;
        private String visionModel;
        private String whisperModel;
        private Double temperature;
    }
    
    @Data
    public static class Schedule {
        private Integer delayMinutes;
        private Integer batchSize;
        private Long delayBetweenMessages;
    }
}
