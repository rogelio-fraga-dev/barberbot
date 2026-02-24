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
@SuppressWarnings("null")
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
    
    // NOVO: Memória para a lista de Retomar
    private static final Map<String, List<String>> adminRetomarOptions = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedRate = 600000) 
    public void clearProcessedCache() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(20);
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(limit));
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "America/Sao_Paulo")
    public void solicitarAgendaAmanha() {
        String adminPhone = properties.getAdmin().getPhone();
        whatsAppService.sendTextMessage(adminPhone, "🌙 Boa noite, Chefe! O expediente está encerrando.\n\n📸 Por favor, me mande a *foto da agenda de amanhã* para eu mapear os clientes.\n\n_Lembrete: Eu vou avisar automaticamente cada cliente exatamente 1 hora antes do corte!_");
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void dispararLembretesMatinais() {
        String adminPhone = properties.getAdmin().getPhone();
        String agenda = agendaService.getAgendaSalva();
        whatsAppService.sendTextMessage(adminPhone, "☀️ Bom dia, Chefe! O robô já acordou. 🤖\n\n📅 *Nossa agenda mapeada para hoje é:*\n\n" + agenda + "\n\n🚀 Fique tranquilo, eu cuidarei de enviar a mensagem de lembrete 1 hora antes para cada um deles!");
    }

    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        try {
            if (shouldIgnoreMessage(webhook)) return;
            if (isDuplicateMessage(webhook)) return;
            
            String phoneNumber = webhook.getPhoneNumber();
            log.info("=====================================================");
            log.info("[WEBHOOK] Mensagem recebida de: {}", phoneNumber);
            
            if (isAdminNumber(phoneNumber)) {
                log.info("[SISTEMA] Identificado como ADMINISTRADOR (Luiz/Sistema).");
                processAdminMessage(webhook, phoneNumber);
            } else {
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("[SISTEMA] Cliente {} está PAUSADO (Atendimento Humano). Bot silenciado.", phoneNumber);
                    return;
                }
                log.info("[SISTEMA] Identificado como CLIENTE.");
                processCustomerMessage(webhook, phoneNumber);
            }
            log.info("=====================================================");

        } catch (Exception e) {
            log.error("[ERRO FATAL NO ORCHESTRATOR] {}", e.getMessage(), e);
        }
    }

    private boolean shouldIgnoreMessage(EvolutionWebhookDTO webhook) {
        if (webhook.getData() == null || webhook.getData().getKey() == null) return true;
        if (Boolean.TRUE.equals(webhook.getData().getKey().getFromMe())) return true;
        if (webhook.isGroupChat() || webhook.getPhoneNumber() == null) return true;
        return false;
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
        String adminEnv = properties.getAdmin().getPhone().replaceAll("[^0-9]", "");
        String phoneIn = phoneNumber.replaceAll("[^0-9]", "");
        
        if (adminEnv.isEmpty()) return false;
        
        String adminBase = adminEnv.startsWith("55") ? adminEnv.substring(2) : adminEnv;
        String phoneBase = phoneIn.startsWith("55") ? phoneIn.substring(2) : phoneIn;
        
        if (adminBase.equals(phoneBase)) return true;
        
        if (adminBase.length() >= 8 && phoneBase.length() >= 8) {
            String last8Admin = adminBase.substring(adminBase.length() - 8);
            String last8Phone = phoneBase.substring(phoneBase.length() - 8);
            if (last8Admin.equals(last8Phone) && adminBase.substring(0, 2).equals(phoneBase.substring(0, 2))) {
                return true;
            }
        }
        return false;
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
    
    // ==========================================
    // FLUXO DO ADMINISTRADOR
    // ==========================================
    private void processAdminMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        String command = null;
        
        if (webhook.hasDocument()) {
            adminStates.remove(phoneNumber);
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ O arquivo CSV chegou corrompido.");
                return;
            }
            whatsAppService.sendTextMessage(phoneNumber, "⏳ Lendo a base de clientes do CSV...");
            try {
                int salvos = customerService.importCustomersFromCsvBase64(base64);
                whatsAppService.sendTextMessage(phoneNumber, "✅ Importação Concluída!\nForam salvos/atualizados *" + salvos + "* clientes.");
            } catch (Exception e) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ Erro ao processar o CSV.");
            }
            return;
        }

        if (webhook.hasImage()) {
            adminStates.remove(phoneNumber);
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ A imagem não pôde ser decodificada.");
                return;
            }
            whatsAppService.sendTextMessage(phoneNumber, "⏳ Visão Computacional ativada. Lendo horários...");
            try {
                String agendaJson = openAIService.extractAgendaFromImage(base64, webhook.getMimeType());
                int salvos = agendaService.processAgenda(agendaJson);
                whatsAppService.sendTextMessage(phoneNumber, "✅ Agenda lida com sucesso! " + salvos + " clientes identificados para receber o lembrete. Digite *6* para conferir.");
            } catch (Exception e) {
                whatsAppService.sendTextMessage(phoneNumber, "❌ Erro na IA ao ler a imagem.");
            }
            return;
        } 
        
        if (webhook.hasAudio()) {
            String base64 = webhook.getBase64() != null ? webhook.getBase64() : fetchBase64FromEvolution(webhook.getData().getKey().getId());
            if (base64 == null) return;
            whatsAppService.sendTextMessage(phoneNumber, "🎧 Ouvindo seu áudio...");
            command = openAIService.transcribeAudio(base64, webhook.getMimeType());
            if (!adminStates.containsKey(phoneNumber) && command != null && !isSystemCommand(command)) {
                whatsAppService.sendTextMessage(phoneNumber, "📝 *Transcrição Livre:*\n" + command);
                return;
            }
        } else {
            command = webhook.getMessageText();
        }
        
        if (command != null) {
            String originalCommand = command.trim();
            String cmdLower = originalCommand.toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
            String currentState = adminStates.get(phoneNumber);
            
            if (currentState != null) {
                if (cmdLower.contains("cancelar") || cmdLower.contains("cancela")) {
                    adminStates.remove(phoneNumber);
                    sendFullMenu(phoneNumber);
                    return;
                }
                if (currentState.equals("AVISO_BASE")) {
                    adminStates.remove(phoneNumber);
                    performBroadcast(phoneNumber, originalCommand, false);
                    return;
                }
                if (currentState.equals("AVISO_PROSPEC")) {
                    adminStates.remove(phoneNumber);
                    performBroadcast(phoneNumber, originalCommand, true);
                    return;
                }
                if (currentState.equals("PAUSAR")) {
                    adminStates.remove(phoneNumber);
                    String target = cmdLower.replaceAll("[^0-9]", "");
                    customerService.pauseCustomer(target, 60);
                    whatsAppService.sendTextMessage(phoneNumber, "⏸️ Robô silenciado com sucesso.");
                    return;
                }
                if (currentState.equals("RETOMAR")) {
                    adminStates.remove(phoneNumber);
                    List<String> options = adminRetomarOptions.remove(phoneNumber);
                    try {
                        int index = Integer.parseInt(cmdLower.replaceAll("[^0-9]", "")) - 1;
                        if (options != null && index >= 0 && index < options.size()) {
                            String targetPhone = options.get(index);
                            customerService.resumeCustomer(targetPhone);
                            Customer c = customerRepository.findByPhoneNumber(targetPhone).orElse(null);
                            String name = (c != null && c.getName() != null) ? c.getName() : targetPhone;
                            whatsAppService.sendTextMessage(phoneNumber, "▶️ Robô religado para: *" + name + "*");
                        } else {
                            whatsAppService.sendTextMessage(phoneNumber, "❌ Opção inválida. Ação cancelada.");
                        }
                    } catch (Exception e) {
                        String target = cmdLower.replaceAll("[^0-9]", "");
                        if (target.length() >= 10) {
                            customerService.resumeCustomer(target);
                            whatsAppService.sendTextMessage(phoneNumber, "▶️ Robô religado.");
                        } else {
                            whatsAppService.sendTextMessage(phoneNumber, "❌ Formato inválido. Ação cancelada.");
                        }
                    }
                    return;
                }
                if (currentState.equals("IMPORTAR_MANUAL")) {
                    adminStates.remove(phoneNumber);
                    processManualImport(phoneNumber, originalCommand);
                    return;
                }
            }
            
            if (cmdLower.contains("comando") || cmdLower.contains("ajuda") || cmdLower.contains("menu")) {
                sendFullMenu(phoneNumber);
            } else if (cmdLower.equals("1") || cmdLower.contains("resumo")) {
                long total = customerRepository.count();
                whatsAppService.sendTextMessage(phoneNumber, "📊 *Resumo BarberBot*\n\n👥 Clientes na Base: " + total + "\n✅ Inteligência Artificial Online.");
            } else if (cmdLower.equals("2") || (cmdLower.contains("aviso") && cmdLower.contains("base"))) {
                adminStates.put(phoneNumber, "AVISO_BASE");
                whatsAppService.sendTextMessage(phoneNumber, "📢 *Disparo 1: Base de Clientes*\n\nEnvie agora a mensagem de aviso.\n_(Diga 'cancelar' para abortar)_");
            } else if (cmdLower.equals("3") || cmdLower.contains("prospec")) {
                adminStates.put(phoneNumber, "AVISO_PROSPEC");
                whatsAppService.sendTextMessage(phoneNumber, "🎯 *Disparo 2: Prospecção*\n\nEnvie a sua mensagem de oferta.");
            } else if (cmdLower.equals("4") || cmdLower.contains("pausar") || cmdLower.contains("pausa")) {
                adminStates.put(phoneNumber, "PAUSAR");
                whatsAppService.sendTextMessage(phoneNumber, "⏸️ *Pausar Robô*\nDigite o número do cliente com DDD.");
            } else if (cmdLower.equals("5") || cmdLower.contains("retomar") || cmdLower.contains("retoma")) {
                List<String> pausedPhones = customerService.getPausedPhones();
                if (pausedPhones.isEmpty()) {
                    whatsAppService.sendTextMessage(phoneNumber, "▶️ *Retomar Robô*\n\nNenhum cliente está pausado no momento.");
                } else {
                    adminStates.put(phoneNumber, "RETOMAR");
                    adminRetomarOptions.put(phoneNumber, pausedPhones); 
                    
                    StringBuilder sb = new StringBuilder("▶️ *Retomar Robô - Clientes Pausados*\n\n");
                    for (int i = 0; i < pausedPhones.size(); i++) {
                        String p = pausedPhones.get(i);
                        Customer c = customerRepository.findByPhoneNumber(p).orElse(null);
                        String name = (c != null && c.getName() != null) ? c.getName() : "Desconhecido";
                        sb.append("*").append(i + 1).append("* - ").append(name).append("\n");
                    }
                    sb.append("\nDigite o *NÚMERO DA OPÇÃO* (Ex: 1) para religar o robô.");
                    whatsAppService.sendTextMessage(phoneNumber, sb.toString());
                }
                
            } else if (cmdLower.equals("6") || (cmdLower.contains("agenda") && !cmdLower.contains("ler"))) {
                whatsAppService.sendTextMessage(phoneNumber, "📅 *Agenda Salva*\n\n" + agendaService.getAgendaSalva());
            } else if (cmdLower.equals("7") || cmdLower.contains("importar")) {
                adminStates.put(phoneNumber, "IMPORTAR_MANUAL");
                whatsAppService.sendTextMessage(phoneNumber, "📥 *Importar Manual*\nDigite: Nome, Telefone");
            } else if (cmdLower.equals("8") || cmdLower.contains("ler agenda")) {
                whatsAppService.sendTextMessage(phoneNumber, "📸 Mande o print da agenda que eu farei a leitura automática.");
            } else {
                whatsAppService.sendTextMessage(phoneNumber, "Fala, Chefe! 💈 O que vamos fazer hoje?\nDigite *comandos* para abrir o Menu Completo.");
            }
        }
    }

    private boolean isSystemCommand(String text) {
        String t = text.toLowerCase().replaceAll("[^a-z ]", "");
        return t.contains("comando") || t.contains("resumo") || t.contains("aviso") || t.contains("prospec") || 
               t.contains("pausar") || t.contains("retomar") || t.contains("agenda") || t.contains("importar");
    }

    private void sendFullMenu(String phoneNumber) {
        String fullMenu = """
                🛠️ *PAINEL CENTRAL - LH BARBEARIA*
                
                *1* - 📊 Ver Resumo
                *2* - 📢 Disparar Avisos (Base)
                *3* - 🎯 Disparar Prospecção
                *4* - ⏸️ Pausar bot (Cliente)
                *5* - ▶️ Retomar bot (Cliente)
                *6* - 📅 Ver Agenda Salva
                *7* - 📥 Importar Cliente (Manual)
                *8* - 📸 Ler Agenda (Foto)
                """;
        whatsAppService.sendTextMessage(phoneNumber, fullMenu);
    }

    private void processManualImport(String phoneNumber, String originalCommand) {
        try {
            String[] parts = originalCommand.split(",");
            if (parts.length >= 2) {
                String name = parts[0].trim();
                String phone = parts[1].replaceAll("[^0-9]", "");
                if (phone.length() == 10 || phone.length() == 11) phone = "55" + phone;
                customerService.findOrCreateCustomer(phone, name);
                whatsAppService.sendTextMessage(phoneNumber, "✅ Cliente *" + name + "* salvo!");
            } else {
                whatsAppService.sendTextMessage(phoneNumber, "❌ Formato incorreto. Use: Nome, Telefone");
            }
        } catch (Exception e) {}
    }
    
    @Async
    public void performBroadcast(String adminPhone, String message, boolean isProspecting) {
        List<Customer> allCustomers = customerRepository.findAll();
        whatsAppService.sendTextMessage(adminPhone, "🚀 Iniciando disparo para " + allCustomers.size() + " contatos...");
        int sent = 0;
        for (Customer customer : allCustomers) {
            try {
                if (customer.getPhoneNumber().contains(adminPhone)) continue;
                String header = isProspecting ? "💈 *LH Barbearia* 💈\n\n" : "📢 *Aviso LH Barbearia*\n\n";
                whatsAppService.sendTextMessage(customer.getPhoneNumber(), header + message);
                sent++;
                Thread.sleep(3000); 
            } catch (Exception e) {}
        }
        whatsAppService.sendTextMessage(adminPhone, "✅ Disparo finalizado! Alcançou " + sent + " contatos.");
    }
    
    // ==========================================
    // FLUXO DO CLIENTE (A RECEPCIONISTA IA)
    // ==========================================
    private void processCustomerMessage(EvolutionWebhookDTO webhook, String phoneNumber) {
        if (webhook.hasSticker()) {
            log.info("[CLIENTE IN] Figurinha recebida de {}. Ignorando silenciosamente.", phoneNumber);
            return;
        }

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
            contentToSave = "[Arquivo]";
        }

        log.info("[CLIENTE IN] Mensagem recebida: '{}'", contentToSave);

        Customer customer = customerService.findOrCreateCustomer(phoneNumber, pushName);
        boolean isFirstMessage = getRecentHistory(customer.getId()).isEmpty();

        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.USER).content(contentToSave).messageId(messageId).build());

        String msgLower = contentToSave.toLowerCase().trim();

        if (msgLower.equals("4") || msgLower.contains("falar com") || msgLower.contains("atendente") || msgLower.contains("luiz")) {
            customerService.pauseCustomer(phoneNumber, 60);
            saveAndSend(customer, "⏳ Certo! Pausei o assistente virtual. Aguarde um instante que o Luiz já vai te atender por aqui mesmo.", phoneNumber);
            return;
        }

        if (isFirstMessage || msgLower.matches("^(oi|olá|ola|bom dia|boa tarde|boa noite|menu).*")) {
            String firstName = pushName != null ? pushName.split(" ")[0] : "amigo(a)";
            
            if (!msgLower.equals("menu")) {
                saveAndSend(customer, "Olá, " + firstName + "! 👋 Bem-vindo(a) à *LH Barbearia*!\n_Corte novo, autoestima renovada!_ 💈", phoneNumber);
                sleep(1000);
            }
            sendFallbackTextMenu(customer, phoneNumber);
            return;
        }

        log.info("[IA PROCESSING] Chamando o GPT-4o...");
        String aiResponse = openAIService.processCustomerMessage(contentToSave, getRecentHistory(customer.getId()));
        log.info("[IA OUT] Resposta gerada: '{}'", aiResponse);
        
        saveAndSend(customer, aiResponse, phoneNumber);
    }

    private void sendFallbackTextMenu(Customer customer, String phone) {
        String textMenu = """
                💈 *Menu Principal - LH Barbearia* 💈
                
                Como posso te ajudar hoje? Responda com o *NÚMERO* da opção:
                
                *1* - ✂️ Serviços, Valores e Produtos
                *2* - 📅 Agendar Horário (Link Direto)
                *3* - 📍 Onde ficamos e Instagram
                *4* - 👤 Falar com o Luiz (Atendimento Humano)
                
                💡 _Dica: Você também pode me mandar um áudio ou perguntar qualquer coisa escrevendo normalmente!_
                """;
        saveAndSend(customer, textMenu, phone);
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