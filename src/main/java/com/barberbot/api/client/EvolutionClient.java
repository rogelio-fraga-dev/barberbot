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
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvolutionClient {

    private final WebClient webClient;
    private final BarberBotProperties properties;

    /**
     * Envia mensagem de texto simples
     */
    public Mono<String> sendTextMessage(String phone, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("number", formatPhone(phone));
        body.put("text", text);
        body.put("delay", 1200);
        body.put("linkPreview", true);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/message/sendText/{instance}")
                        .build(properties.getEvolution().getInstanceName()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", properties.getEvolution().getApiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Erro ao enviar texto para {}: {}", phone, e.getMessage()));
    }

    /**
     * Envia imagem com legenda
     */
    public Mono<String> sendImageMessage(String phone, String imageUrl, String caption) {
        Map<String, Object> body = new HashMap<>();
        body.put("number", formatPhone(phone));
        body.put("media", imageUrl);
        body.put("mediatype", "image");
        body.put("caption", caption);
        body.put("delay", 1200);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/message/sendMedia/{instance}")
                        .build(properties.getEvolution().getInstanceName()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", properties.getEvolution().getApiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Erro ao enviar imagem para {}: {}", phone, e.getMessage()));
    }

    /**
     * Envia DTO genérico (usado internamente se necessário)
     */
    public Mono<String> sendMessage(MessageDTO messageDTO) {
        return sendTextMessage(messageDTO.getNumber(), messageDTO.getText());
    }

    /**
     * Envia LISTA INTERATIVA (Botões de Menu)
     * Este é o método novo que o WhatsAppService está chamando.
     */
    public Mono<String> sendListMessage(String phone, String title, String description, 
                                        String buttonText, String footerText, 
                                        List<Map<String, Object>> sections) {
        
        Map<String, Object> body = new HashMap<>();
        body.put("number", formatPhone(phone));
        body.put("title", title);
        body.put("description", description);
        body.put("buttonText", buttonText);
        body.put("footer", footerText);
        body.put("sections", sections);
        body.put("delay", 1000);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/message/sendList/{instance}")
                        .build(properties.getEvolution().getInstanceName()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", properties.getEvolution().getApiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Erro ao enviar lista para {}: {}", phone, e.getMessage()));
    }

    // --- MÉTODOS ESTÁTICOS AUXILIARES (Usados pelo MenuOptions) ---
    
    public static Map<String, String> listRow(String id, String title, String description) {
        Map<String, String> row = new HashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("description", description);
        return row;
    }

    public static Map<String, Object> listSection(String title, List<Map<String, String>> rows) {
        Map<String, Object> section = new HashMap<>();
        section.put("title", title);
        section.put("rows", rows);
        return section;
    }

    // --- UTILS ---

    private String formatPhone(String phone) {
        if (phone == null) return "";
        // Remove caracteres não numéricos
        String nums = phone.replaceAll("[^0-9]", "");
        // Se não tiver DDI (comprimento 10 ou 11), adiciona 55
        if (nums.length() == 10 || nums.length() == 11) {
            return "55" + nums;
        }
        return nums;
    }
}