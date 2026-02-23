package com.barberbot.api.service;

import com.barberbot.api.config.BarberBotProperties;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {
    
    private final OpenAiChatModel chatModel;
    private final BarberBotProperties properties;
    private WebClient openAiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é o assistente virtual oficial da **LH Barbearia** em Araguari, MG.
            Seu objetivo é ser cordial, ágil e refletir a frase: "Corte novo, autoestima renovada!".
            
            📋 **Informações da Barbearia:**
            - **Endereço:** R. Floriano Peixoto, 585 - Miranda, Araguari.
            - **Horário:** Seg a Sáb, 09:00 às 20:00 (Almoço 12:00 às 14:00).
            
            ⚙️ **Regras de Atendimento:**
            1. Seja breve.
            2. Se quiserem agendar, peça para digitar "4" ou mande o link do CashBarber.
            3. Para preços, peça para digitar "2".
            """;
    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em ler prints de sistemas de agendamento (CashBarber).
            Sua tarefa: Analisar a imagem e extrair os agendamentos.
            Retorne APENAS um JSON válido (sem markdown) no formato:
            {"items": [{"name": "Nome", "phone": "5534999999999", "time": "14:30", "service": "Corte"}]}
            """;
    
    public String processCustomerMessage(String userMessage, List<String> recentHistory) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(SYSTEM_PROMPT_RECEPTIONIST));
            if (recentHistory != null) {
                for (int i = 0; i < recentHistory.size(); i++) {
                    if (i % 2 == 0) messages.add(UserMessage.from(recentHistory.get(i)));
                    else messages.add(AiMessage.from(recentHistory.get(i)));
                }
            }
            messages.add(UserMessage.from(userMessage));
            return chatModel.generate(messages).content().text();
        } catch (Exception e) {
            log.error("Erro Chat: {}", e.getMessage());
            return "Estou terminando um corte aqui! Pode repetir?";
        }
    }
    
    public String extractAgendaFromImage(String base64Image, String mimeType) {
        try {
            String pureBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
            String cleanMime = (mimeType != null && mimeType.contains("image/")) ? mimeType.split(";")[0] : "image/jpeg";
            
            UserMessage userMessage = UserMessage.from(
                TextContent.from("Extraia os agendamentos desta imagem para JSON."),
                ImageContent.from(pureBase64, cleanMime) 
            );
            SystemMessage systemMessage = SystemMessage.from(SYSTEM_PROMPT_AGENDA_READER);
            String response = chatModel.generate(systemMessage, userMessage).content().text();
            return response.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            log.error("Erro Visão: {}", e.getMessage());
            throw new RuntimeException("Falha ao ler imagem.", e);
        }
    }
    
    public String transcribeAudio(String base64Audio, String mimeType) {
        try {
            String pureBase64 = base64Audio.contains(",") ? base64Audio.split(",")[1] : base64Audio;
            byte[] audioBytes = Base64.getDecoder().decode(pureBase64);
            
            String extension = "ogg"; 
            if (mimeType != null) {
                if (mimeType.contains("mp4")) extension = "mp4";
                else if (mimeType.contains("mpeg") || mimeType.contains("mp3")) extension = "mp3";
            }
            final String filename = "audio." + extension;
            
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(audioBytes) {
                @Override public String getFilename() { return filename; }
            });
            builder.part("model", "whisper-1");

            String jsonResponse = getOpenAiWebClient()
                    .post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // Leitura Limpa e Oficial do JSON
            if (jsonResponse != null) {
                JsonNode root = objectMapper.readTree(jsonResponse);
                if (root.has("text")) {
                    return root.get("text").asText().trim();
                }
            }
            return jsonResponse;
        } catch (Exception e) {
            log.error("Erro Áudio: {}", e.getMessage());
            return "[Erro na transcrição do áudio]";
        }
    }

    private WebClient getOpenAiWebClient() {
        if (openAiWebClient == null) {
            openAiWebClient = WebClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .build();
        }
        return openAiWebClient;
    }
}