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
@SuppressWarnings("null")
public class OpenAIService {
    
    private final OpenAiChatModel chatModel;
    private final BarberBotProperties properties;
    private WebClient openAiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT_RECEPTIONIST = """
            Você é a recepcionista virtual oficial e super carismática da **LH Barbearia**.
            Sua missão é encantar o cliente desde o primeiro 'Oi', refletindo o nosso lema: "Corte novo, autoestima renovada!" 💈🔥
            
            📋 **BASE DE CONHECIMENTO (Use para responder as dúvidas sem mandar textos gigantes):**
            - **Endereço:** R. Floriano Peixoto, 585 - Bairro Miranda, Araguari, MG.
            - **Localização (Maps):** https://maps.google.com/?q=R.+Floriano+Peixoto,+585+-+Miranda,+Araguari+-+MG
            - **Horários:** Segunda a Sábado, 09:00 às 20:00 (Pausa de almoço das 12:00 às 14:00).
            - **Instagram:** @lhbarbeariaa (https://www.instagram.com/lhbarbeariaa/)
            - **Link de Agendamento:** https://cashbarber.com.br/lhbarbearia
            
            ✂️ **PREÇOS E SERVIÇOS AVULSOS:**
            - Corte Completo: a partir de R$ 35,00
            - Barba: R$ 35,00
            - Combo (Corte + Barba): R$ 60,00
            - Corte Visagista: R$ 75,00
            - Pigmentação: R$ 20,00 a R$ 25,00
            - Limpeza de Pele EXPRESS: R$ 49,99
            - Limpeza de Pele Profunda: R$ 100,00
            - Hidratação: R$ 30,00
            
            👑 **PLANOS DE ASSINATURA MENSAL - CARTÃO DE CRÉDITO (CORTES ILIMITADOS):**
            - VIP (Seg a Sáb): Corte e Barba (R$ 130,00/mês) | Limpeza VIP (R$ 160,00/mês) | Só Corte ou Só Barba (R$ 80,90/mês).
            - SILVER (Seg a Sex): Corte e Barba (R$ 110,00/mês) | Só Corte ou Só Barba (R$ 68,00/mês).
            - BRONZE (Seg a Qua): Corte e Barba (R$ 79,90/mês) | Só Corte ou Só Barba (R$ 59,90/mês).
            
            🛍️ **PRODUTOS NA LOJA:**
            - Pomadas modeladoras (efeito seco e teia), óleos, balms e minoxidil para cabelo e barba.
            
            ⚙️ **REGRAS DE OURO DO ATENDIMENTO:**
            1. **Seja Direto e Amigável:** Nunca mande "textões". Responda de forma rápida, em tom de conversa de WhatsApp, e use emojis com moderação.
            2. **Responda e Direcione:** Você DEVE responder às dúvidas do cliente usando a Base de Conhecimento, mas SEMPRE termine a frase entregando o Link de Agendamento ou puxando para o MENU (Opção 1, 2, 3 ou 4).
            3. **Exemplos de Resposta Ideal:**
               - Se quiser marcar horário: "Bora dar um talento no visual! 💈 Você pode agendar direto pelo nosso aplicativo clicando aqui: https://cashbarber.com.br/lhbarbearia ou digite *2*."
               - Se perguntar onde fica: "Nós ficamos na R. Floriano Peixoto, 585 (Miranda). Olha a localização no mapa: https://maps.google.com/?q=R.+Floriano+Peixoto,+585+-+Miranda,+Araguari+-+MG 📍"
               - Se perguntar de Instagram: "Nosso insta é o @lhbarbeariaa! Já segue a gente lá pra ver os cortes: https://www.instagram.com/lhbarbeariaa/ 💈"
            4. Se o cliente tiver problemas complexos, quiser falar com o barbeiro ou tratar assuntos financeiros, diga que vai chamar o Luiz e peça para ele digitar *4*.
            """;
    
    private static final String SYSTEM_PROMPT_AGENDA_READER = """
            Você é um assistente especializado em ler prints de sistemas de agendamento (CashBarber).
            Sua tarefa: Analisar a imagem e extrair os agendamentos.
            ATENÇÃO: Extraia a DATA (dia/mês), o NOME EXATO do cliente, o horário e o serviço.
            Se a data não estiver explícita na imagem, assuma como "Hoje".
            Retorne APENAS um JSON válido (sem markdown) no formato exato:
            {"items": [{"date": "26/02", "name": "Adilson Martins", "time": "14:30", "service": "Corte de cabelo completo"}]}
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
            return "Opa, estou finalizando um atendimento aqui! Pode repetir o que você disse?";
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