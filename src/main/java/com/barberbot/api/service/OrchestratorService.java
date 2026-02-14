package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.dto.EvolutionWebhookDTO;
import com.barberbot.api.model.Customer;
import com.barberbot.api.model.Interaction;
import com.barberbot.api.repository.CustomerRepository;
import com.barberbot.api.repository.InteractionRepository;
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

    // Cache de IDs para evitar duplicatas (Idempotência em Memória)
    private static final Map<String, LocalDateTime> processedMessageIds = new ConcurrentHashMap<>();

    /**
     * Limpa o cache de IDs antigos a cada 10 minutos.
     */
    @Scheduled(fixedRate = 600000) 
    public void clearProcessedCache() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(20);
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue().isBefore(limit));
    }

    /**
     * Processa o Webhook recebido.
     */
    @Async
    public void processWebhook(EvolutionWebhookDTO webhook) {
        try {
            if (shouldIgnoreMessage(webhook)) return;
            
            // --- BARREIRA DE IDEMPOTÊNCIA ---
            if (isDuplicateMessage(webhook)) return;
            // --------------------------------
            
            String phoneNumber = webhook.getPhoneNumber();
            log.info("Processando webhook do número: {}", phoneNumber);
            
            if (isAdminNumber(phoneNumber)) {
                processAdminMessage(webhook);
            } else {
                // Se o cliente estiver em "Modo Pausa" (atendimento humano), o bot ignora.
                if (customerService.isCustomerPaused(phoneNumber)) {
                    log.info("Cliente {} está em pausa (atendimento humano). Bot silenciado.", phoneNumber);
                    return;
                }
                processCustomerMessage(webhook);
            }

        } catch (Exception e) {
            log.error("Erro fatal no processamento assíncrono do webhook: {}", e.getMessage(), e);
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
        String adminPhone = properties.getAdmin().getPhone();
        String normalizedPhone = phoneNumber != null ? phoneNumber.replaceAll("[^0-9]", "") : "";
        String normalizedAdmin = adminPhone != null ? adminPhone.replaceAll("[^0-9]", "") : "";
        
        if (normalizedAdmin.isEmpty()) return false;
        
        if (normalizedPhone.equals(normalizedAdmin)) return true;
        if (normalizedPhone.equals("55" + normalizedAdmin)) return true;
        if (normalizedAdmin.length() == 11 && normalizedPhone.endsWith(normalizedAdmin)) return true;
        
        return false;
    }
    
    /**
     * Lógica exclusiva para o Administrador (Luiz).
     */
    private void processAdminMessage(EvolutionWebhookDTO webhook) {
        String phoneNumber = webhook.getPhoneNumber();
        
        // 1. Processamento de Imagem (Agenda)
        if (webhook.hasImage()) {
            whatsAppService.sendTextMessage(phoneNumber, "⏳ Analisando agenda...");
            try {
                String agendaJson = openAIService.extractAgendaFromImage(webhook.getImageUrl());
                int tasksCreated = agendaService.processAgenda(agendaJson);
                whatsAppService.sendTextMessage(phoneNumber, "✅ Agenda processada! " + tasksCreated + " agendamentos criados (lembretes + avaliações).");
            } catch (Exception e) {
                log.error("Erro agenda: {}", e.getMessage());
                whatsAppService.sendTextMessage(phoneNumber, "❌ Erro ao ler imagem da agenda.");
            }
            return;
        } 
        
        // 2. Processamento de Áudio (Transcrição)
        if (webhook.hasAudio()) {
            try {
                String transcription = openAIService.transcribeAudio(webhook.getAudioUrl());
                whatsAppService.sendTextMessage(phoneNumber, "📝 Transcrição: " + transcription);
            } catch (Exception e) {
                log.error("Erro áudio: {}", e.getMessage());
                whatsAppService.sendTextMessage(phoneNumber, "❌ Erro ao transcrever áudio.");
            }
            return;
        } 
        
        // 3. Comandos de Texto Administrativos
        String text = webhook.getMessageText();
        if (text != null) {
            String command = text.trim();
            
            // --- COMANDO: LISTAR COMANDOS (AJUDA) ---
            if (command.equalsIgnoreCase("comandos") || command.equalsIgnoreCase("ajuda") || command.equalsIgnoreCase("help")) {
                String helpMessage = """
                        🛠️ *MANUAL DE COMANDOS - ADMIN*
                        
                        📸 *Envie Foto da Agenda*: O bot lê os horários e agenda lembretes automáticos.
                        
                        🎤 *Envie Áudio*: O bot transcreve o áudio para texto.
                        
                        📊 *Resumo*: Mostra quantos clientes e mensagens temos na base.
                        
                        ⏸️ *Pausar: [numero]*
                        _Ex: Pausar: 34999998888_
                        Silencia o bot para esse cliente por 1 hora (pra você assumir).
                        
                        ▶️ *Retomar: [numero]*
                        _Ex: Retomar: 34999998888_
                        O bot volta a responder esse cliente imediatamente.
                        
                        📢 *Avisar: [mensagem]*
                        _Ex: Avisar: Estamos sem energia hoje!_
                        Envia a mensagem para TODOS os clientes cadastrados.
                        
                        📥 *Importar: [num],[nome]; [num],[nome]*
                        Cadastra clientes em massa.
                        
                        🎯 *Prospeccao: [msg] ALVOS: [n1],[n2]*
                        Envia mensagem para números que não estão na base (leads).
                        """;
                whatsAppService.sendTextMessage(phoneNumber, helpMessage);
                return;
            }
            
            // Comando: Resumo / Status
            if (command.equalsIgnoreCase("resumo") || command.equalsIgnoreCase("status") || command.equalsIgnoreCase("info")) {
                long totalClientes = customerRepository.count();
                long totalInteracoes = interactionRepository.count();
                whatsAppService.sendTextMessage(phoneNumber, String.format("📊 *Status BarberBot*\n👥 Clientes: %d\n💬 Msgs: %d\n✅ Online", totalClientes, totalInteracoes));
                return;
            }
            
            // Comando: Pausar
            if (command.toLowerCase().startsWith("pausar:")) {
                String targetPhone = command.substring(7).trim().replaceAll("[^0-9]", "");
                if (!targetPhone.isEmpty()) {
                    customerService.pauseCustomer(targetPhone, 60); 
                    whatsAppService.sendTextMessage(phoneNumber, "⏸️ Bot pausado para " + targetPhone + " por 1 hora.");
                } else {
                    whatsAppService.sendTextMessage(phoneNumber, "⚠️ Formato inválido. Use: Pausar: [numero]");
                }
                return;
            }
            
            // Comando: Retomar
            if (command.toLowerCase().startsWith("retomar:")) {
                String targetPhone = command.substring(8).trim().replaceAll("[^0-9]", "");
                if (!targetPhone.isEmpty()) {
                    customerService.resumeCustomer(targetPhone);
                    whatsAppService.sendTextMessage(phoneNumber, "▶️ Bot retomado para " + targetPhone);
                } else {
                    whatsAppService.sendTextMessage(phoneNumber, "⚠️ Formato inválido. Use: Retomar: [numero]");
                }
                return;
            }

            // Comando: Importar Lista
            if (command.toLowerCase().startsWith("importar:")) {
                String lista = command.substring(9).trim();
                int imported = customerService.importContactsFromText(lista);
                whatsAppService.sendTextMessage(phoneNumber, "✅ Importação concluída! " + imported + " contatos processados.");
                return;
            }

            // Comando: Prospecção
            if (command.toLowerCase().startsWith("prospeccao:")) {
                try {
                    String[] parts = command.split("ALVOS:");
                    if (parts.length < 2) {
                        whatsAppService.sendTextMessage(phoneNumber, "⚠️ Formato inválido. Use: Prospeccao: [Msg] ALVOS: num1,num2");
                        return;
                    }
                    String msg = parts[0].substring(11).trim(); 
                    String[] numbers = parts[1].split(",");
                    
                    whatsAppService.sendTextMessage(phoneNumber, "🚀 Enviando prospecção para " + numbers.length + " números...");
                    
                    new Thread(() -> {
                        for (String num : numbers) {
                            String target = num.trim().replaceAll("[^0-9]", "");
                            if (!target.isEmpty()) {
                                whatsAppService.sendTextMessage(target, msg);
                                try { Thread.sleep(2000); } catch (InterruptedException e) {} 
                            }
                        }
                        whatsAppService.sendTextMessage(phoneNumber, "✅ Prospecção finalizada.");
                    }).start();
                } catch (Exception e) {
                    whatsAppService.sendTextMessage(phoneNumber, "❌ Erro na prospecção: " + e.getMessage());
                }
                return;
            }

            // Comando: Avisar (Broadcast)
            if (command.toLowerCase().startsWith("avisar:") || command.toLowerCase().startsWith("aviso:")) {
                int separatorIndex = command.indexOf(":");
                String broadcastMessage = command.substring(separatorIndex + 1).trim();
                
                if (broadcastMessage.length() < 5) {
                    whatsAppService.sendTextMessage(phoneNumber, "⚠️ Mensagem muito curta. Digite: 'Avisar: [sua mensagem]'");
                    return;
                }
                performBroadcast(phoneNumber, broadcastMessage);
                return;
            }
            
            // Fallback: Se não reconhecer o comando, sugere digitar COMANDOS
            whatsAppService.sendTextMessage(phoneNumber, "🤖 Comando não reconhecido.\nDigite *COMANDOS* para ver a lista de opções.");
        }
    }
    
    @Async
    public void performBroadcast(String adminPhone, String message) {
        List<Customer> allCustomers = customerRepository.findAll();
        int total = allCustomers.size();
        int sent = 0;
        
        whatsAppService.sendTextMessage(adminPhone, "🚀 Iniciando disparo para " + total + " clientes cadastrados...");
        
        for (Customer customer : allCustomers) {
            try {
                if (customer.getPhoneNumber().contains(adminPhone) || adminPhone.contains(customer.getPhoneNumber())) {
                    continue;
                }
                String personalizedMessage = "📢 *Aviso LH Barbearia*\n\n" + message;
                whatsAppService.sendTextMessage(customer.getPhoneNumber(), personalizedMessage);
                sent++;
                Thread.sleep(2000); 
            } catch (Exception e) {
                log.error("Falha ao enviar broadcast para {}", customer.getPhoneNumber());
            }
        }
        whatsAppService.sendTextMessage(adminPhone, "✅ Disparo finalizado! Enviado com sucesso para " + sent + " clientes.");
    }
    
    private void processCustomerMessage(EvolutionWebhookDTO webhook) {
        String phoneNumber = webhook.getPhoneNumber();
        String messageText = webhook.getMessageText();
        String pushName = webhook.getData() != null ? webhook.getData().getPushName() : null;
        String contentToSave = messageText != null ? messageText : "[Mídia]";
        String messageId = webhook.getData().getKey().getId();

        Customer customer = customerService.findOrCreateCustomer(phoneNumber, pushName);

        interactionRepository.save(Interaction.builder()
                .customer(customer).type(Interaction.InteractionType.USER)
                .content(contentToSave).messageId(messageId).build());

        String menuOptionId = MenuOptions.resolveMenuOptionId(contentToSave);
        if (menuOptionId != null) {
            String response = MenuOptions.getResponseForOption(menuOptionId, properties);
            response += "\n\n_(Clique em *Ver Opções* para voltar)_";
            saveAndSend(customer, response, phoneNumber);
            return;
        }

        if (MenuOptions.isAskingForMenu(contentToSave)) {
            sendInteractiveMenu(customer, phoneNumber);
            return;
        }

        List<String> recentHistory = getRecentHistory(customer.getId());
        String aiResponse = openAIService.processCustomerMessage(
                messageText != null ? messageText : "Olá",
                recentHistory
        );

        saveAndSend(customer, aiResponse, phoneNumber);

        if (aiResponse != null && (aiResponse.toLowerCase().contains("menu") || 
                                   aiResponse.contains("opções") || 
                                   aiResponse.contains("opcoes"))) {
            sleep(1000);
            sendInteractiveMenu(customer, phoneNumber);
        }
    }

    private void sendInteractiveMenu(Customer customer, String phone) {
        try {
            whatsAppService.sendListMessage(phone, "💈 Menu LH Barbearia", "Escolha uma opção 👇", "Ver Opções", "LH Barbearia", MenuOptions.buildListSections());
            interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.BOT).content("[Menu Interativo]").build());
        } catch (Exception e) {
            String textMenu = MenuOptions.getMenuAsText();
            saveAndSend(customer, textMenu, phone);
        }
    }

    private void saveAndSend(Customer customer, String content, String phone) {
        interactionRepository.save(Interaction.builder().customer(customer).type(Interaction.InteractionType.BOT).content(content).build());
        whatsAppService.sendTextMessage(phone, content);
    }
    
    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    
    private List<String> getRecentHistory(java.util.UUID customerId) {
        return interactionRepository.findRecentInteractionsByCustomerId(customerId).stream()
                .map(Interaction::getContent)
                .collect(Collectors.toList());
    }
}