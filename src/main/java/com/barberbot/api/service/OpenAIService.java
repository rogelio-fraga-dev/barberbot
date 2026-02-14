package com.barberbot.api.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {
    
    private final OpenAiChatModel chatModel;
    
    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é uma recepcionista profissional e formal de uma barbearia.
            Seu nome é BarberBot Assist.
            
            Regras de atendimento:
            1. Seja sempre educada, profissional e prestativa
            2. Mantenha respostas objetivas e úteis
            3. Se não souber algo, ofereça transferir para o atendente humano
            4. Sempre que relevante, ofereça o menu de opções
            
            Menu disponível:
            📍 Endereço (Texto + Google Maps)
            💰 Serviços e Tabela de Preços
            💈 Produtos (Fotos e Valores)
            📅 Agendar Horário (Envia Link Externo)
            🗣️ Falar com Atendente (Para o robô e chama o Luiz)
            📸 Instagram (nos siga nas redes)
            
            Quando o cliente pedir algo específico do menu, responda adequadamente.
            Pode sugerir "Ver opções" ou "menu" para o cliente abrir o menu com botões.
            """;
    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em extrair informações de imagens de agendas e planilhas.
            
            Sua tarefa é analisar a imagem fornecida e extrair:
            - Horários de atendimento
            - Nomes dos clientes
            - Números de telefone (apenas números, sem espaços ou caracteres especiais)
            - Tipo de serviço (se disponível)
            
            Retorne APENAS um JSON válido no seguinte formato:
            {
              "items": [
                {
                  "name": "Nome do Cliente",
                  "phone": "34984141504",
                  "time": "14:30",
                  "service": "Corte"
                }
              ]
            }
            
            IMPORTANTE:
            - Se não conseguir identificar claramente um dado, não invente
            - Telefones devem ter apenas dígitos (ex: 34984141504)
            - Horários devem estar no formato HH:mm (24 horas)
            """;
    
    /**
     * Processa uma mensagem do cliente e gera uma resposta usando IA
     */
    public String processCustomerMessage(String userMessage, List<String> recentHistory) {
        try {
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            
            // Adiciona o prompt do sistema
            messages.add(SystemMessage.from(SYSTEM_PROMPT_RECEPTIONIST));
            
            // Adiciona histórico recente (últimas 5 mensagens)
            if (recentHistory != null && !recentHistory.isEmpty()) {
                int historySize = Math.min(5, recentHistory.size());
                for (int i = Math.max(0, recentHistory.size() - historySize); i < recentHistory.size(); i++) {
                    // Alterna entre USER e AI (simplificado)
                    if (i % 2 == 0) {
                        messages.add(UserMessage.from(recentHistory.get(i)));
                    } else {
                        messages.add(AiMessage.from(recentHistory.get(i)));
                    }
                }
            }
            
            // Adiciona a mensagem atual
            messages.add(UserMessage.from(userMessage));
            
            // Gera resposta
            AiMessage response = chatModel.generate(messages).content();
            
            log.info("Resposta gerada pela IA para mensagem: {}", userMessage.substring(0, Math.min(50, userMessage.length())));
            return response.text();
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem com IA: {}", e.getMessage(), e);
            return "Desculpe, ocorreu um erro ao processar sua mensagem. Por favor, tente novamente ou peça para falar com um atendente.";
        }
    }
    
    /**
     * Lê uma imagem de agenda e extrai informações estruturadas
     */
    public String extractAgendaFromImage(String imageUrl) {
        try {
            log.info("Processando imagem de agenda: {}", imageUrl);
            
            // Usa o modelo de visão para analisar a imagem
            // Nota: LangChain4j ainda não tem suporte direto para Vision no momento desta versão
            // Pode ser necessário usar a API do OpenAI diretamente ou atualizar a biblioteca
            
            String prompt = "Analise esta imagem de agenda e extraia os horários, nomes e telefones. " +
                          "Retorne apenas JSON válido conforme especificado no prompt do sistema.";
            
            // TODO: Implementar chamada para Vision API quando disponível no LangChain4j
            // Por enquanto, retorna um placeholder
            log.warn("Vision API ainda não implementada completamente. Usando placeholder.");
            
            return """
                {
                  "items": [
                    {
                      "name": "Cliente Exemplo",
                      "phone": "34984141504",
                      "time": "14:30",
                      "service": "Corte"
                    }
                  ]
                }
                """;
                
        } catch (Exception e) {
            log.error("Erro ao processar imagem de agenda: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar imagem de agenda", e);
        }
    }
    
    /**
     * Transcreve um áudio usando Whisper
     */
    public String transcribeAudio(String audioUrl) {
        try {
            log.info("Transcrevendo áudio: {}", audioUrl);
            
            // TODO: Implementar transcrição usando Whisper API
            // LangChain4j pode não ter suporte direto, pode ser necessário chamar API diretamente
            
            log.warn("Transcrição de áudio ainda não implementada completamente.");
            return "Áudio transcrito (placeholder)";
            
        } catch (Exception e) {
            log.error("Erro ao transcrever áudio: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao transcrever áudio", e);
        }
    }
}
