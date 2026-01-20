package com.barberbot.api.service;

import com.barberbot.api.client.EvolutionClient;
import com.barberbot.api.dto.MessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {
    
    private final EvolutionClient evolutionClient;
    
    /**
     * Envia uma mensagem de texto
     */
    public void sendTextMessage(String phone, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendTextMessage(phone, message).block();
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Envia uma mensagem genérica
     */
    public void sendMessage(MessageDTO messageDTO) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendMessage(messageDTO).block();
            } catch (Exception e) {
                log.error("Erro ao enviar mensagem: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Envia uma imagem com legenda
     */
    public void sendImage(String phone, String imageUrl, String caption) {
        CompletableFuture.runAsync(() -> {
            try {
                evolutionClient.sendImageMessage(phone, imageUrl, caption).block();
            } catch (Exception e) {
                log.error("Erro ao enviar imagem para {}: {}", phone, e.getMessage(), e);
            }
        });
    }
}
