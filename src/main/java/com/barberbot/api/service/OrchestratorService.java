package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.dto.EvolutionWebhookDTO;
import com.barberbot.api.model.Customer;
import com.barberbot.api.model.Interaction;
import com.barberbot.api.repository.InteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {
    
    private final CustomerService customerService;
    private final InteractionRepository interactionRepository;
    private final OpenAIService openAIService;
    private final WhatsAppService whatsAppService;
    private final AgendaService agendaService;
    private final BarberBotProperties properties;
    
    /**
     * Processa um webhook recebido da Evolution API
     * Este é o ponto central de decisão do sistema
     */
    @Transactional
    public void processWebhook(EvolutionWebhookDTO webhook) {
        // Ignora mensagens enviadas pelo próprio bot
        if (webhook.getData() != null && 
            webhook.getData().getKey() != null && 
            Boolean.TRUE.equals(webhook.getData().getKey().getFromMe())) {
            log.debug("Ignorando mensagem enviada pelo próprio bot");
            return;
        }

        // Ignora mensagens em grupos (só atendemos chat 1:1 com o número da barbearia)
        if (webhook.isGroupChat()) {
            log.debug("Ignorando mensagem de grupo (bot só responde em chat privado)");
            return;
        }
        
        String phoneNumber = webhook.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            log.warn("Webhook recebido sem número de telefone válido");
            return;
        }
        
        log.info("Processando webhook do número: {}", phoneNumber);
        
        // Verifica se é o admin (Luiz)
        boolean isAdmin = isAdminNumber(phoneNumber);
        
        if (isAdmin) {
            processAdminMessage(webhook);
        } else {
            processCustomerMessage(webhook);
        }
    }
    
    /**
     * Verifica se o número pertence ao admin.
     * Aceita admin configurado com ou sem código do país (55);
     * a Evolution envia o número completo (ex: 5534984141504).
     */
    private boolean isAdminNumber(String phoneNumber) {
        String adminPhone = properties.getAdmin().getPhone();
        String normalizedPhone = phoneNumber != null ? phoneNumber.replaceAll("[^0-9]", "") : "";
        String normalizedAdmin = adminPhone != null ? adminPhone.replaceAll("[^0-9]", "") : "";
        if (normalizedAdmin.isEmpty()) return false;
        // Exato (ex: 34984141504 == 34984141504)
        if (normalizedPhone.equals(normalizedAdmin)) return true;
        // Com código do Brasil: 55 + admin (ex: 5534984141504)
        if (normalizedPhone.equals("55" + normalizedAdmin)) return true;
        // Admin 11 dígitos (DDD+numero): aceita se o webhook mandar só os 11 no final
        if (normalizedAdmin.length() == 11 && normalizedPhone.endsWith(normalizedAdmin)) return true;
        return false;
    }
    
    /**
     * Processa mensagem do admin (Luiz)
     */
    private void processAdminMessage(EvolutionWebhookDTO webhook) {
        log.info("Mensagem recebida do ADMIN");
        
        String phoneNumber = webhook.getPhoneNumber();
        
        // Verifica se há imagem (agenda)
        if (webhook.hasImage()) {
            String imageUrl = webhook.getImageUrl();
            log.info("Imagem de agenda recebida do admin");
            
            try {
                // Extrai informações da agenda
                String agendaJson = openAIService.extractAgendaFromImage(imageUrl);
                
                // Processa e agenda tarefas
                int tasksCreated = agendaService.processAgenda(agendaJson);
                
                // Responde ao admin
                String response = String.format(
                    "✅ Agenda processada com sucesso!\n" +
                    "Agendei %d avaliações para hoje.",
                    tasksCreated
                );
                
                whatsAppService.sendTextMessage(phoneNumber, response);
                
            } catch (Exception e) {
                log.error("Erro ao processar agenda: {}", e.getMessage(), e);
                whatsAppService.sendTextMessage(phoneNumber, 
                    "❌ Erro ao processar agenda. Verifique se a imagem está legível.");
            }
        }
        // Verifica se há áudio
        else if (webhook.hasAudio()) {
            log.info("Áudio recebido do admin");
            try {
                String audioUrl = webhook.getAudioUrl();
                String transcription = openAIService.transcribeAudio(audioUrl);
                log.info("Áudio transcrito: {}", transcription);
                // Processa o comando do admin baseado na transcrição
                // TODO: Implementar parser de comandos do admin
            } catch (Exception e) {
                log.error("Erro ao processar áudio: {}", e.getMessage(), e);
            }
        }
        // Mensagem de texto do admin
        else {
            String messageText = webhook.getMessageText();
            if (messageText != null) {
                log.info("Mensagem de texto do admin: {}", messageText);
                // TODO: Implementar comandos do admin (disparo geral, etc.)
                whatsAppService.sendTextMessage(phoneNumber, 
                    "Comando recebido. Funcionalidades administrativas em desenvolvimento.");
            }
        }
    }
    
    /**
     * Processa mensagem de um cliente
     */
    private void processCustomerMessage(EvolutionWebhookDTO webhook) {
        String phoneNumber = webhook.getPhoneNumber();
        String messageText = webhook.getMessageText();
        String pushName = webhook.getData() != null ? webhook.getData().getPushName() : null;
        String contentToSave = messageText != null ? messageText : "[Mensagem sem texto]";

        log.info("Mensagem recebida do cliente {}: {}", phoneNumber, messageText);

        // Busca ou cria cliente
        Customer customer = customerService.findOrCreateCustomer(phoneNumber, pushName);

        // Salva a mensagem do cliente no histórico
        Interaction userInteraction = Interaction.builder()
                .customer(customer)
                .type(Interaction.InteractionType.USER)
                .content(contentToSave)
                .messageId(webhook.getData() != null && webhook.getData().getKey() != null ?
                        webhook.getData().getKey().getId() : null)
                .build();
        interactionRepository.save(userInteraction);

        // 1) Cliente escolheu opção do menu (lista ou digitação 1-6) -> resposta pré-definida + menu de novo
        String menuOptionId = MenuOptions.resolveMenuOptionId(contentToSave);
        if (menuOptionId != null) {
            String response = MenuOptions.getResponseForOption(menuOptionId, properties);
            Interaction botInteraction = Interaction.builder()
                    .customer(customer)
                    .type(Interaction.InteractionType.BOT)
                    .content(response)
                    .build();
            interactionRepository.save(botInteraction);
            whatsAppService.sendTextMessage(phoneNumber, response);
            whatsAppService.sendMenuList(phoneNumber);
            return;
        }

        // 2) Cliente pediu para ver o menu -> envia lista interativa (clicável)
        if (MenuOptions.isAskingForMenu(contentToSave)) {
            String shortText = "Aqui estão nossas opções:";
            Interaction botInteraction = Interaction.builder()
                    .customer(customer)
                    .type(Interaction.InteractionType.BOT)
                    .content(shortText + " [menu interativo enviado]")
                    .build();
            interactionRepository.save(botInteraction);
            whatsAppService.sendTextMessage(phoneNumber, shortText);
            whatsAppService.sendMenuList(phoneNumber);
            return;
        }

        // 3) Resposta da IA
        List<String> recentHistory = getRecentHistory(customer.getId());
        String aiResponse = openAIService.processCustomerMessage(
                messageText != null ? messageText : "Olá",
                recentHistory
        );

        Interaction botInteraction = Interaction.builder()
                .customer(customer)
                .type(Interaction.InteractionType.BOT)
                .content(aiResponse)
                .build();
        interactionRepository.save(botInteraction);

        whatsAppService.sendTextMessage(phoneNumber, aiResponse);

        // Se a IA sugeriu o menu, envia a lista interativa para o cliente poder tocar
        if (aiResponse != null && (aiResponse.toLowerCase().contains("menu") || aiResponse.contains("opções") || aiResponse.contains("opcoes"))) {
            whatsAppService.sendMenuList(phoneNumber);
        }
    }
    
    /**
     * Obtém o histórico recente de interações do cliente
     */
    private List<String> getRecentHistory(java.util.UUID customerId) {
        List<Interaction> interactions = interactionRepository
                .findRecentInteractionsByCustomerId(customerId);
        
        return interactions.stream()
                .limit(10) // Últimas 10 interações
                .map(Interaction::getContent)
                .collect(Collectors.toList());
    }
}
