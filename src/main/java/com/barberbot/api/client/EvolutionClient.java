package com.barberbot.api.client;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.dto.MessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvolutionClient {
    
    private final BarberBotProperties properties;
    private final WebClient webClient;
    
    /**
     * Envia uma mensagem de texto via Evolution API
     */
    public Mono<String> sendTextMessage(String phone, String message) {
        String url = String.format("/message/sendText/%s", properties.getEvolution().getInstanceName());
        
        Map<String, Object> body = new HashMap<>();
        body.put("number", phone);
        body.put("text", message);
        
        log.info("Enviando mensagem para {} via Evolution API", phone);
        
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Mensagem enviada com sucesso para {}", phone))
                .doOnError(error -> log.error("Erro ao enviar mensagem para {}: {}", phone, error.getMessage()));
    }
    
    /**
     * Envia uma imagem via Evolution API
     */
    public Mono<String> sendImageMessage(String phone, String imageUrl, String caption) {
        String url = String.format("/message/sendMedia/%s", properties.getEvolution().getInstanceName());
        
        Map<String, Object> body = new HashMap<>();
        body.put("number", phone);
        body.put("mediaUrl", imageUrl);
        body.put("caption", caption != null ? caption : "");
        body.put("fileName", "image.jpg");
        
        log.info("Enviando imagem para {} via Evolution API", phone);
        
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Imagem enviada com sucesso para {}", phone))
                .doOnError(error -> log.error("Erro ao enviar imagem para {}: {}", phone, error.getMessage()));
    }
    
    /**
     * Envia uma mensagem genérica (texto ou mídia)
     */
    public Mono<String> sendMessage(MessageDTO messageDTO) {
        if (messageDTO.getMediaUrl() != null && !messageDTO.getMediaUrl().isEmpty()) {
            return sendImageMessage(messageDTO.getPhone(), messageDTO.getMediaUrl(), messageDTO.getMessage());
        } else {
            return sendTextMessage(messageDTO.getPhone(), messageDTO.getMessage());
        }
    }
}
