package com.barberbot.api.controller;

import com.barberbot.api.dto.EvolutionWebhookDTO;
import com.barberbot.api.service.OrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final OrchestratorService orchestratorService;
    private final ObjectMapper objectMapper;

    /**
     * Endpoint que recebe webhooks da Evolution API (mensagens e QR Code).
     * Aceita JSON flexivel para tratar evento de QR Code que o Manager nao exibe.
     */
    @PostMapping
    public ResponseEntity<String> receiveWebhook(@RequestBody Map<String, Object> body) {
        try {
            String event = body.get("event") != null ? body.get("event").toString() : null;
            String instance = body.get("instance") != null ? body.get("instance").toString() : null;
            // Só loga em INFO os eventos que importam (mensagens e QR); presence.update etc em DEBUG
            boolean relevante = event != null && (
                event.equalsIgnoreCase("messages.upsert") || event.equalsIgnoreCase("MESSAGES_UPSERT")
                || event.equalsIgnoreCase("qrcode.updated") || event.equalsIgnoreCase("QRCODE_UPDATED"));
            if (relevante) {
                log.info("Webhook - {} | {}", event, instance);
            } else {
                log.debug("Webhook - {} | {}", event, instance);
            }

            // Evento de QR Code: salva a imagem em arquivo (contorna bug do Manager que nao exibe)
            if (event != null && (event.equalsIgnoreCase("qrcode.updated") || event.equalsIgnoreCase("QRCODE_UPDATED"))) {
                String qrPath = salvarQrCodeDoWebhook(body);
                if (qrPath != null) {
                    log.info("QR Code salvo em: {}", qrPath);
                    return ResponseEntity.ok("QR Code recebido e salvo em: " + qrPath);
                }
            }

            // Evento de mensagens: delega para o orquestrador
            if ("messages.upsert".equalsIgnoreCase(event) || "MESSAGES_UPSERT".equalsIgnoreCase(event)) {
                EvolutionWebhookDTO webhook = objectMapper.convertValue(body, EvolutionWebhookDTO.class);
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
     * Extrai o QR Code do payload do webhook e salva em arquivo PNG.
     * Retorna o caminho do arquivo ou null se nao houver base64.
     */
    private String salvarQrCodeDoWebhook(Map<String, Object> body) {
        try {
            Object data = body.get("data");
            if (data == null || !(data instanceof Map)) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            String base64 = null;

            if (dataMap.get("base64") != null) {
                base64 = dataMap.get("base64").toString();
            } else if (dataMap.get("qr") != null) {
                String qr = dataMap.get("qr").toString();
                if (qr.startsWith("data:image")) {
                    int comma = qr.indexOf(',');
                    if (comma > 0) base64 = qr.substring(comma + 1);
                } else if (qr.length() > 100) {
                    base64 = qr;
                }
            }

            if (base64 == null || base64.isBlank()) return null;

            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            if (bytes.length == 0) return null;

            Path dir = Paths.get(System.getProperty("user.dir"));
            Path file = dir.resolve("qrcode-evolution.png");
            Files.write(file, bytes);
            return file.toAbsolutePath().toString();
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Nao foi possivel salvar QR Code do webhook: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Endpoint de health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BarberBot Assist está online!");
    }

    /**
     * Exibe o ultimo QR Code recebido pelo webhook (para quando o Manager nao mostra).
     * Abra no navegador: http://localhost:8081/api/webhook/qrcode
     */
    @GetMapping(value = "/qrcode", produces = "image/png")
    public ResponseEntity<byte[]> getQrCodeImage() {
        try {
            Path file = Paths.get(System.getProperty("user.dir")).resolve("qrcode-evolution.png");
            if (!Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = Files.readAllBytes(file);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "inline; filename=qrcode-evolution.png")
                    .body(bytes);
        } catch (IOException e) {
            log.warn("Erro ao ler QR Code: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
