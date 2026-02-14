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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é o assistente virtual oficial da **LH Barbearia** em Araguari, MG.
            Seu objetivo é ser cordial, ágil e refletir a frase: "Corte novo, autoestima renovada!".
            
            📋 **Informações da Barbearia:**
            - **Endereço:** R. Floriano Peixoto, 585 - Miranda, Araguari.
            - **Horário de Funcionamento:** Segunda a Sábado, das 09:00 às 20:00.
            - **Almoço:** Fechado das 12:00 às 14:00.
            - **Seu Horário (Bot):** Você atende 24 horas por dia para tirar dúvidas e mandar links.
            
            ⚙️ **Regras de Atendimento:**
            1. Seja breve. Respostas curtas funcionam melhor no WhatsApp.
            2. Se o cliente quiser agendar, SEMPRE mande o link do CashBarber ou peça para digitar "4".
            3. Se perguntarem preço, dê um exemplo (ex: Corte a partir de R$35) e peça para digitar "2" para ver a tabela completa com Planos VIP.
            4. Se for algo complexo que você não sabe, peça para digitar "5" (Falar com Luiz).
            5. Nunca invente preços que não estão na sua base.
            
            💬 **Estilo de Fala:**
            Profissional mas acessível. Use emojis com moderação (✂️, 💈, 🔥).
            
            Opções do Menu (sugira se o cliente estiver perdido):
            1. Endereço
            2. Preços/Serviços
            3. Produtos
            4. Agendar
            5. Falar com Luiz
            6. Instagram
            """;
    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em ler prints de sistemas de agendamento (CashBarber).
            
            Sua tarefa: Analisar a imagem e extrair os agendamentos.
            Retorne APENAS um JSON válido (sem markdown, sem ```json) no formato:
            {
              "items": [
                {
                  "name": "Nome do Cliente",
                  "phone": "5534999999999",
                  "time": "14:30",
                  "service": "Corte"
                }
              ]
            }
            
            Regras Críticas:
            1. Extraia o telefone apenas com números. Se não tiver DDI (55), adicione se for Brasil.
            2. Se o telefone não estiver visível, deixe vazio ou tente inferir.
            3. Horário deve ser HH:mm.
            """;
    
    /**
     * Chat com Cliente (Texto)
     */
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
            log.error("Erro no Chat IA: {}", e.getMessage(), e);
            return "Desculpe, estou terminando um corte aqui! Pode tentar novamente em instantes?";
        }
    }
    
    /**
     * Visão Computacional: Ler Agenda
     */
    public String extractAgendaFromImage(String imageUrl) {
        try {
            log.info("Baixando imagem da agenda: {}", imageUrl);
            String base64Image = downloadUrlAsBase64(imageUrl);
            
            UserMessage userMessage = UserMessage.from(
                TextContent.from("Analise esta imagem e extraia os agendamentos em JSON."),
                ImageContent.from(base64Image, "image/jpeg")
            );
            
            SystemMessage systemMessage = SystemMessage.from(SYSTEM_PROMPT_AGENDA_READER);
            
            log.info("Enviando imagem para GPT-4o Vision...");
            String response = chatModel.generate(systemMessage, userMessage).content().text();
            
            return response.replace("```json", "").replace("```", "").trim();
                
        } catch (Exception e) {
            log.error("Erro ao processar imagem de agenda: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao ler imagem da agenda. Verifique se está legível.", e);
        }
    }
    
    /**
     * Audição: Transcrever Áudio (Whisper via HTTP Raw)
     */
    public String transcribeAudio(String audioUrl) {
        try {
            log.info("Baixando áudio para transcrição: {}", audioUrl);
            byte[] audioBytes = downloadUrlBytes(audioUrl);
            
            log.info("Enviando áudio ({} bytes) para Whisper API...", audioBytes.length);
            
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "audio.mp3";
                }
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
            
            if (jsonResponse != null && jsonResponse.contains("\"text\":")) {
                int start = jsonResponse.indexOf("\"text\":") + 8;
                String text = jsonResponse.substring(start);
                text = text.substring(0, text.lastIndexOf("\""));
                if (text.startsWith("\"")) text = text.substring(1);
                return text.replace("\\n", "\n").replace("\\\"", "\"");
            }
            
            return jsonResponse;

        } catch (Exception e) {
            log.error("Erro ao transcrever áudio: {}", e.getMessage(), e);
            return "[Erro ao ouvir áudio]";
        }
    }

    private WebClient getOpenAiWebClient() {
        if (openAiWebClient == null) {
            openAiWebClient = WebClient.builder()
                    .baseUrl("[https://api.openai.com/v1](https://api.openai.com/v1)")
                    .defaultHeader("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .build();
        }
        return openAiWebClient;
    }
    
    private String downloadUrlAsBase64(String urlString) throws IOException {
        byte[] bytes = downloadUrlBytes(urlString);
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    private byte[] downloadUrlBytes(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (InputStream in = new BufferedInputStream(url.openStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while (-1 != (n = in.read(buffer))) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}