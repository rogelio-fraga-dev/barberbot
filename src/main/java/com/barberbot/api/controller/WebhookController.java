package com.barberbot.api.controller;

import com.barberbot.api.dto.EvolutionWebhookDTO;
import com.barberbot.api.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {
    
    private final OrchestratorService orchestratorService;
    
    /**
     * Endpoint que recebe webhooks da Evolution API
     */
    @PostMapping
    public ResponseEntity<String> receiveWebhook(@RequestBody EvolutionWebhookDTO webhook) {
        try {
            log.info("Webhook recebido - Evento: {}, Instância: {}", 
                    webhook.getEvent(), webhook.getInstance());
            
            // Processa apenas eventos de mensagens
            if ("messages.upsert".equals(webhook.getEvent())) {
                orchestratorService.processWebhook(webhook);
            }
            
            return ResponseEntity.ok("Webhook recebido com sucesso");
            
        } catch (Exception e) {
            log.error("Erro ao processar webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao processar webhook");
        }
    }
    
    /**
     * Endpoint de health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BarberBot Assist está online!");
    }
}
