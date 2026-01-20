package com.barberbot.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EvolutionWebhookDTO {
    @JsonProperty("event")
    private String event;
    
    @JsonProperty("instance")
    private String instance;
    
    @JsonProperty("data")
    private WebhookData data;
    
    @Data
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
    public static class MessageKey {
        @JsonProperty("remoteJid")
        private String remoteJid;
        
        @JsonProperty("fromMe")
        private Boolean fromMe;
        
        @JsonProperty("id")
        private String id;
    }
    
    @Data
    public static class Message {
        @JsonProperty("conversation")
        private String conversation;
        
        @JsonProperty("imageMessage")
        private ImageMessage imageMessage;
        
        @JsonProperty("audioMessage")
        private AudioMessage audioMessage;
        
        @JsonProperty("extendedTextMessage")
        private ExtendedTextMessage extendedTextMessage;
    }
    
    @Data
    public static class ImageMessage {
        @JsonProperty("caption")
        private String caption;
        
        @JsonProperty("mimetype")
        private String mimetype;
        
        @JsonProperty("url")
        private String url;
    }
    
    @Data
    public static class AudioMessage {
        @JsonProperty("mimetype")
        private String mimetype;
        
        @JsonProperty("url")
        private String url;
        
        @JsonProperty("seconds")
        private Integer seconds;
    }
    
    @Data
    public static class ExtendedTextMessage {
        @JsonProperty("text")
        private String text;
    }
    
    // Helper methods
    public String getPhoneNumber() {
        if (data != null && data.getKey() != null) {
            String remoteJid = data.getKey().getRemoteJid();
            if (remoteJid != null && remoteJid.contains("@")) {
                return remoteJid.split("@")[0];
            }
        }
        return null;
    }
    
    public String getMessageText() {
        if (data == null || data.getMessage() == null) {
            return null;
        }
        
        Message msg = data.getMessage();
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
