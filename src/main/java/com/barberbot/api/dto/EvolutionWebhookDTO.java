package com.barberbot.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionWebhookDTO {

    @JsonProperty("event")
    private String event;

    @JsonProperty("instance")
    private String instance;

    @JsonProperty("data")
    private DataDTO data;

    @JsonProperty("sender")
    private String sender; // Número do remetente (às vezes vem aqui)

    // --- Métodos Utilitários Inteligentes ---

    public boolean isGroupChat() {
        return data != null && data.key != null && data.key.remoteJid != null && data.key.remoteJid.endsWith("@g.us");
    }

    public boolean hasImage() {
        return data != null && data.message != null && data.message.imageMessage != null;
    }
    
    public boolean hasAudio() {
        return data != null && data.message != null && 
               (data.message.audioMessage != null || data.message.voiceMessage != null); // Evolution às vezes chama de voiceMessage
    }

    public String getImageUrl() {
        if (!hasImage()) return null;
        // A URL geralmente vem no base64 ou url da Evolution, dependendo da config.
        // Assumindo que a Evolution está configurada para retornar a URL da mídia
        return data.message.imageMessage.url; 
    }
    
    public String getAudioUrl() {
        if (!hasAudio()) return null;
        if (data.message.audioMessage != null) return data.message.audioMessage.url;
        if (data.message.voiceMessage != null) return data.message.voiceMessage.url;
        return null;
    }

    /**
     * O CÉREBRO DA LEITURA:
     * Extrai o texto, seja de uma mensagem normal, de uma legenda de foto, 
     * ou de um CLIQUE EM BOTÃO (List Response).
     */
    public String getMessageText() {
        if (data == null || data.message == null) return null;

        // 1. Texto Simples (Conversation)
        if (data.message.conversation != null && !data.message.conversation.isEmpty()) {
            return data.message.conversation;
        }

        // 2. Texto Estendido (ExtendedTextMessage)
        if (data.message.extendedTextMessage != null && data.message.extendedTextMessage.text != null) {
            return data.message.extendedTextMessage.text;
        }

        // 3. Resposta de Imagem (Legenda)
        if (data.message.imageMessage != null && data.message.imageMessage.caption != null) {
            return data.message.imageMessage.caption;
        }

        // 4. CLIQUE EM LISTA (O Menu Interativo!)
        if (data.message.listResponseMessage != null && 
            data.message.listResponseMessage.singleSelectReply != null) {
            // Retorna o ID do botão clicado (ex: "menu_agendar") para o Orchestrator processar
            return data.message.listResponseMessage.singleSelectReply.selectedRowId;
        }

        // 5. Clique em Botão Simples (ButtonsResponseMessage)
        if (data.message.buttonsResponseMessage != null) {
            return data.message.buttonsResponseMessage.selectedButtonId;
        }

        return null;
    }
    
    public String getPhoneNumber() {
        if (data != null && data.key != null && data.key.remoteJid != null) {
            return data.key.remoteJid.replace("@s.whatsapp.net", "");
        }
        return null;
    }

    // --- Estruturas Internas do JSON da Evolution ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataDTO {
        @JsonProperty("key")
        private KeyDTO key;

        @JsonProperty("pushName")
        private String pushName;

        @JsonProperty("message")
        private MessageDTO message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeyDTO {
        @JsonProperty("remoteJid")
        private String remoteJid;

        @JsonProperty("fromMe")
        private Boolean fromMe;

        @JsonProperty("id")
        private String id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageDTO {
        @JsonProperty("conversation")
        private String conversation;

        @JsonProperty("extendedTextMessage")
        private ExtendedTextMessageDTO extendedTextMessage;

        @JsonProperty("imageMessage")
        private ImageMessageDTO imageMessage;
        
        @JsonProperty("audioMessage")
        private AudioMessageDTO audioMessage;

        @JsonProperty("voiceMessage") // Às vezes a Evolution manda como voiceMessage
        private AudioMessageDTO voiceMessage;

        @JsonProperty("listResponseMessage")
        private ListResponseMessageDTO listResponseMessage;

        @JsonProperty("buttonsResponseMessage")
        private ButtonsResponseMessageDTO buttonsResponseMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedTextMessageDTO {
        @JsonProperty("text")
        private String text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageMessageDTO {
        @JsonProperty("caption")
        private String caption;
        @JsonProperty("url") // URL da imagem para download
        private String url;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AudioMessageDTO {
        @JsonProperty("url")
        private String url;
    }

    // --- DTOs para Menu Interativo ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListResponseMessageDTO {
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("singleSelectReply")
        private SingleSelectReplyDTO singleSelectReply;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SingleSelectReplyDTO {
        @JsonProperty("selectedRowId")
        private String selectedRowId; // É aqui que vem o "menu_agendar"
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ButtonsResponseMessageDTO {
        @JsonProperty("selectedButtonId")
        private String selectedButtonId;
    }
}