package com.barberbot.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Evolution API v2 envia campos extras (ex: remoteJidAlt)
public class EvolutionWebhookDTO {
    @JsonProperty("event")
    private String event;
    
    @JsonProperty("instance")
    private String instance;
    
    @JsonProperty("data")
    private WebhookData data;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookData {
        @JsonProperty("key")
        private MessageKey key;
        
        @JsonProperty("message")
        private Message message;
        
        @JsonProperty("messageType")
        private String messageType;
        
        @JsonProperty("pushName")
        private String pushName;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageKey {
        @JsonProperty("remoteJid")
        private String remoteJid;
        
        @JsonProperty("fromMe")
        private Boolean fromMe;
        
        @JsonProperty("id")
        private String id;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("conversation")
        private String conversation;

        @JsonProperty("imageMessage")
        private ImageMessage imageMessage;

        @JsonProperty("audioMessage")
        private AudioMessage audioMessage;

        @JsonProperty("extendedTextMessage")
        private ExtendedTextMessage extendedTextMessage;

        /** Resposta quando o usuário toca em uma opção da lista interativa (rowId) */
        @JsonProperty("listResponseMessage")
        private ListResponseMessage listResponseMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListResponseMessage {
        @JsonProperty("singleSelectReply")
        private SingleSelectReply singleSelectReply;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SingleSelectReply {
        @JsonProperty("selectedRowId")
        private String selectedRowId;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageMessage {
        @JsonProperty("caption")
        private String caption;
        
        @JsonProperty("mimetype")
        private String mimetype;
        
        @JsonProperty("url")
        private String url;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AudioMessage {
        @JsonProperty("mimetype")
        private String mimetype;
        
        @JsonProperty("url")
        private String url;
        
        @JsonProperty("seconds")
        private Integer seconds;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtendedTextMessage {
        @JsonProperty("text")
        private String text;
    }
    
    // Helper methods
    /** WhatsApp: grupos = @g.us, chat privado = @s.whatsapp.net. Só processamos chat privado. */
    public boolean isGroupChat() {
        if (data == null || data.getKey() == null) return false;
        String remoteJid = data.getKey().getRemoteJid();
        return remoteJid != null && remoteJid.endsWith("@g.us");
    }

    public String getPhoneNumber() {
        if (data != null && data.getKey() != null) {
            String remoteJid = data.getKey().getRemoteJid();
            if (remoteJid != null && remoteJid.contains("@")) {
                String part = remoteJid.split("@")[0];
                // Só retorna como "número" se for chat privado (não grupo)
                if (remoteJid.endsWith("@s.whatsapp.net")) return part;
            }
        }
        return null;
    }
    
    public String getMessageText() {
        if (data == null || data.getMessage() == null) {
            return null;
        }

        Message msg = data.getMessage();
        // Resposta da lista interativa (usuário tocou em uma opção) -> rowId
        if (msg.getListResponseMessage() != null && msg.getListResponseMessage().getSingleSelectReply() != null) {
            String rowId = msg.getListResponseMessage().getSingleSelectReply().getSelectedRowId();
            if (rowId != null && !rowId.isEmpty()) return rowId;
        }
        if (msg.getConversation() != null) {
            return msg.getConversation();
        }
        if (msg.getExtendedTextMessage() != null && msg.getExtendedTextMessage().getText() != null) {
            return msg.getExtendedTextMessage().getText();
        }
        if (msg.getImageMessage() != null && msg.getImageMessage().getCaption() != null) {
            return msg.getImageMessage().getCaption();
        }
        return null;
    }
    
    public boolean hasImage() {
        return data != null && 
               data.getMessage() != null && 
               data.getMessage().getImageMessage() != null;
    }
    
    public boolean hasAudio() {
        return data != null && 
               data.getMessage() != null && 
               data.getMessage().getAudioMessage() != null;
    }
    
    public String getImageUrl() {
        if (hasImage()) {
            return data.getMessage().getImageMessage().getUrl();
        }
        return null;
    }
    
    public String getAudioUrl() {
        if (hasAudio()) {
            return data.getMessage().getAudioMessage().getUrl();
        }
        return null;
    }
}
