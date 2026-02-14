package com.barberbot.api.service;

import com.barberbot.api.client.EvolutionClient;
import com.barberbot.api.dto.MessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {
    
    private final EvolutionClient evolutionClient;
    
    public void sendTextMessage(String phone, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendTextMessage(phone, message).block();
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }
    
    public void sendMessage(MessageDTO messageDTO) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendMessage(messageDTO).block();
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem: {}", e.getMessage(), e);
            }
        });
    }
    
    public void sendImage(String phone, String imageUrl, String caption) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendImageMessage(phone, imageUrl, caption).block();
            } catch (Exception e) {
                log.error("Erro ao enviar imagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }

    public void sendMenuList(String phone) {
        sendTextMessage(phone, MenuOptions.getMenuAsText());
    }

    /**
     * Envia lista interativa (Botões)
     */
    public void sendListMessage(String phone, String title, String description, 
                                String buttonText, String footerText, 
                                List<Map<String, Object>> sections) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendListMessage(phone, title, description, buttonText, footerText, sections).block();
            } catch (Exception e) {
                log.error("Erro ao enviar lista interativa para {}: {}", phone, e.getMessage());
                // Lança exceção para o Orchestrator pegar e fazer fallback
                throw new RuntimeException(e);
            }
        });
    }
}       