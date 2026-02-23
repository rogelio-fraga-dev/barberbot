package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.dto.EvolutionWebhookDTO;
import com.barberbot.api.model.Customer;
import com.barberbot.api.model.Interaction;
import com.barberbot.api.repository.CustomerRepository;
import com.barberbot.api.repository.InteractionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {
    
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final InteractionRepository interactionRepository;
    private final OpenAIService openAIService;
    private final WhatsAppService whatsAppService;
    private final AgendaService agendaService;
    private final BarberBotProperties properties;

    private static final Map<String, LocalDateTime> processedMessageIds = new ConcurrentHashMap<>();
    private static final Map<String, String> adminStates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedRate = 600000) 
    public void clearProcessedCache() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(20);
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(limit));
    }

    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        try {
            if (shouldIgnoreMessage(webhook)) return;
            if (isDuplicateMessage(webhook)) return;
            
            String phoneNumber = webhook.getPhoneNumber();
            log.info("=====================================================");
            log.info("[WEBHOOK] Nova mensagem de: {}", phoneNumber);
            
            if (isAdminNumber(phoneNumber)) {
                log.info("[SISTEMA] Usuário autenticado como ADMINISTRADOR.");
                processAdminMessage(webhook, phoneNumber);
            } else {
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("[SISTEMA] Cliente {} está PAUSADO. Bot não enviará resposta.", phoneNumber);
                    return;
                }
                log.info("[SISTEMA] Usuário identificado como CLIENTE.");
                processCustomerMessage(webhook, phoneNumber);
            }
            log.info("=====================================================");

        } catch (Exception e) {
            log.error("[ERRO FATAL] {}", e.getMessage(), e);
        }
    }

    private boolean shouldIgnoreMessage(EvolutionWebhookDTO webhook) {
        if (webhook.getData() == null || webhook.getData().getKey() == null) return true;
        if (Boolean.TRUE.equals(webhook.getData().getKey().getFromMe())) return true;
        if (webhook.isGroupChat()) return true;
        return webhook.getPhoneNumber() == null || webhook.getPhoneNumber().isEmpty();
    }

    private boolean isDuplicateMessage(EvolutionWebhookDTO webhook) {
        String messageId = webhook.getData().getKey().getId();
        if (messageId != null) {
            if (processedMessageIds.putIfAbsent(messageId, LocalDateTime.now()) != null) return true;
            if (interactionRepository.existsByMessageId(messageId)) return true;
        }
        return false;
    }
    
    private boolean isAdminNumber(String phoneNumber) {
        String adminPhone = properties.getAdmin().getPhone().replaceAll("[^0-9]", "");
        String normalizedPhone = phoneNumber.replaceAll("[^0-9]", "");
        if (adminPhone.isEmpty()) return false;
        return normalizedPhone.equals(adminPhone) || normalizedPhone.equals("55" + adminPhone) || 
               (adminPhone.length() == 11 && normalizedPhone.endsWith(adminPhone));
    }

    private String fetchBase64FromEvolution(String messageId) {
        try {
            String baseUrl = properties.getEvolution().getBaseUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            String url = baseUrl + "/chat/getBase64FromMediaMessage/" + properties.getEvolution().getInstanceName();
            String jsonPayload = String.format("{\"message\":{\"key\":{\"id\":\"%s\"}}}", messageId);
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("apikey", properties.getEvolution().getApiKey())
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.body());
                if (root.has("base64") && !root.get("base64").isNull()) return root.get("base64").asText();
            }
        } catch (Exception e) {}
        return null;
    }
    
    private void processAdminMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        String command = null;
        
        if (webhook.hasImage()) {
            adminStates.remove(phoneNumber);
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ O arquivo da imagem chegou corrompido.");
                return;
            }
            whatsAppService.sendTextMessage(phoneNumber, "⏳ Lendo horários da agenda...");
            try {
                String agendaJson = openAIService.extractAgendaFromImage(base64, webhook.getMimeType());
                int tasksCreated = agendaService.processAgenda(agendaJson);
                whatsAppService.sendTextMessage(phoneNumber, "✅ Agenda salva! " + tasksCreated + " clientes receberão lembretes.");
            } catch (Exception e) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ Erro ao ler a imagem. Tente mandar uma foto mais nítida.");
            }
            return;
        } 
        
        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ Ocorreu um erro ao baixar o áudio.");
                return;
            }
            whatsAppService.sendTextMessage(phoneNumber, "🎧 Ouvindo...");
            command = openAIService.transcribeAudio(base64, webhook.getMimeType());
            log.info("[ADMIN] Áudio transcrito: '{}'", command);
            whatsAppService.sendTextMessage(phoneNumber, "📝 _\"" + command + "\"_");
        } else {
            command = webhook.getMessageText();
        }
        
        if (command != null) {
            String originalCommand = command.trim();
            // Limpa pontuações para que áudios como "Comandos." virem "comandos"
            String cmdLower = originalCommand.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
            String currentState = adminStates.get(phoneNumber);
            
            if (currentState != null) {
                if (cmdLower.equals("cancelar")) {
                    adminStates.remove(phoneNumber);
                    whatsAppService.sendTextMessage(phoneNumber, "❌ Ação cancelada.");
                    sendFullMenu(phoneNumber);
                    return;
                }
                if (currentState.equals("AVISAR")) {
                    adminStates.remove(phoneNumber);
                    performBroadcast(phoneNumber, originalCommand);
                    return;
                }
                if (currentState.equals("PAUSAR")) {
                    adminStates.remove(phoneNumber);
                    String targetPhone = cmdLower.replaceAll("[^0-9]", "");
                    customerService.pauseCustomer(targetPhone, 60);
                    whatsAppService.sendTextMessage(phoneNumber, "⏸️ Bot silenciado para " + targetPhone);
                    return;
                }
                if (currentState.equals("RETOMAR")) {
                    adminStates.remove(phoneNumber);
                    String targetPhone = cmdLower.replaceAll("[^0-9]", "");
                    customerService.resumeCustomer(targetPhone);
                    whatsAppService.sendTextMessage(phoneNumber, "▶️ Bot ativado para " + targetPhone);
                    return;
                }
            }
            
            if (cmdLower.startsWith("comando") || cmdLower.equals("ajuda") || cmdLower.equals("menu")) {
                sendFullMenu(phoneNumber);
            } else if (cmdLower.equals("1")) {
                whatsAppService.sendTextMessage(phoneNumber, String.format("📊 *Status BarberBot*\n👥 Clientes na Base: %d", customerRepository.count()));
            } else if (cmdLower.equals("2") || cmdLower.startsWith("avisa")) {
                adminStates.put(phoneNumber, "AVISAR");
                whatsAppService.sendTextMessage(phoneNumber, "📢 *Modo Disparo (Avisar Todos)*\nDigite a mensagem ou *Mande um Áudio* com o aviso.\n\n_(Para abortar, diga 'cancelar')_");
            } else if (cmdLower.equals("3") || cmdLower.startsWith("pausa")) {
                adminStates.put(phoneNumber, "PAUSAR");
                whatsAppService.sendTextMessage(phoneNumber, "⏸️ *Pausar Bot*\nDigite o número do cliente (com DDD).\n\n_(Para abortar, diga 'cancelar')_");
            } else if (cmdLower.equals("4") || cmdLower.startsWith("retoma")) {
                adminStates.put(phoneNumber, "RETOMAR");
                whatsAppService.sendTextMessage(phoneNumber, "▶️ *Retomar Bot*\nDigite o número do cliente (com DDD).\n\n_(Para abortar, diga 'cancelar')_");
            } else {
                whatsAppService.sendTextMessage(phoneNumber, "Fala, Chefe! 💈 O que vamos fazer hoje?\n\n*1* 📊 Status\n*2* 📢 Disparar Mensagem\n*3* ⏸️ Pausar cliente\n*4* ▶️ Retomar cliente\n\n_(Diga *comandos* para o manual)_");
            }
        }
    }

    private void sendFullMenu(String phoneNumber) {
        String fullMenu = """
                🛠️ *PAINEL CENTRAL - BARBERBOT*
                
                *Ações Interativas (Digite o Número ou Fale o Comando):*
                *1* - 📊 Status e Resumo
                *2* - 📢 Avisar Todos
                *3* - ⏸️ Pausar bot para um cliente
                *4* - ▶️ Retomar bot para um cliente
                
                *Atalhos Rápidos:*
                📸 *Foto da Agenda:* Mande um print da agenda para programar lembretes.
                🎤 *Áudio:* O Bot entende comandos de voz! Diga "Avisar" e depois grave o seu recado.
                """;
        whatsAppService.sendTextMessage(phoneNumber, fullMenu);
    }
    
    @Async
    public void performBroadcast(String adminPhone, String message) {
        List<Customer> allCustomers = customerRepository.findAll();
        whatsAppService.sendTextMessage(adminPhone, "🚀 Preparando disparo para " + allCustomers.size() + " clientes...");
        int sent = 0;
        for (Customer customer : allCustomers) {
            try {
                if (customer.getPhoneNumber().contains(adminPhone)) continue;
                whatsAppService.sendTextMessage(customer.getPhoneNumber(), "📢 *Aviso LH Barbearia*\n\n" + message);
                sent++;
                Thread.sleep(2000); 
            } catch (Exception e) {}
        }
        whatsAppService.sendTextMessage(adminPhone, "✅ Broadcast finalizado! Mensagem enviada para " + sent + " clientes.");
    }
    
    private void processCustomerMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        String pushName = webhook.getData() != null ? webhook.getData().getPushName() : null;
        String messageId = webhook.getData().getKey().getId();
        
        String contentToSave = webhook.getMessageText();
        
        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 != null) {
                contentToSave = openAIService.transcribeAudio(base64, webhook.getMimeType());
            } else {
                contentToSave = "[Áudio]";
            }
        } else if (contentToSave == null) {
            contentToSave = "[Mídia]";
        }

        Customer customer = customerService.findOrCreateCustomer(phoneNumber, pushName);
        boolean isFirstMessage = getRecentHistory(customer.getId()).isEmpty();

        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.USER).content(contentToSave).messageId(messageId).build());

        String menuOptionId = MenuOptions.resolveMenuOptionId(contentToSave);
        if (menuOptionId != null) {
            String response = MenuOptions.getResponseForOption(menuOptionId, properties);
            if (menuOptionId.equals(MenuOptions.ROW_ID_ATENDENTE)) {
                customerService.pauseCustomer(phoneNumber, 60);
            } else {
                response += "\n\n_(Clique em *Ver Opções* para voltar)_";
            }
            saveAndSend(customer, response, phoneNumber);
            return;
        }

        if (MenuOptions.isAskingForMenu(contentToSave)) {
            sendInteractiveMenu(customer, phoneNumber);
            return;
        }

        if (isFirstMessage || contentToSave.toLowerCase().matches("^(oi|olá|ola|bom dia|boa tarde|boa noite).*")) {
            String firstName = pushName != null ? pushName.split(" ")[0] : "amigo(a)";
            saveAndSend(customer, "Olá, " + firstName + "! 👋 Bem-vindo(a) à *LH Barbearia*!\nSempre que quiser ver opções, digite *MENU*.", phoneNumber);
            sleep(1000);
            sendInteractiveMenu(customer, phoneNumber);
            return;
        }

        String aiResponse = openAIService.processCustomerMessage(contentToSave, getRecentHistory(customer.getId()));
        saveAndSend(customer, aiResponse, phoneNumber);
    }

    private void sendInteractiveMenu(Customer customer, String phone) {
        try {
            whatsAppService.sendListMessage(phone, "💈 Menu LH Barbearia", "Escolha uma opção 👇", "Ver Opções", "LH Barbearia", MenuOptions.buildListSections());
        } catch (Exception e) {
            saveAndSend(customer, MenuOptions.getMenuAsText(), phone);
        }
    }

    private void saveAndSend(Customer customer, String content, String phone) {
        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.BOT).content(content).build());
        whatsAppService.sendTextMessage(phone, content);
    }
    
    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) {}
    }
    
    private List<String> getRecentHistory(java.util.UUID customerId) {
        return interactionRepository.findRecentInteractionsByCustomerId(customerId).stream()
                .map(Interaction::getContent).collect(Collectors.toList());
    }
}