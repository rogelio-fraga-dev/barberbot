package com.barberbot.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvolutionWebhookDTO {

    @JsonProperty("event") private String event;
    @JsonProperty("instance") private String instance;
    @JsonProperty("data") private DataDTO data;
    @JsonProperty("sender") private String sender;

    public boolean isGroupChat() {
        return data != null && data.key != null && data.key.remoteJid != null && data.key.remoteJid.endsWith("@g.us");
    }

    public boolean hasImage() {
        return data != null && data.message != null && data.message.imageMessage != null;
    }
    
    public boolean hasAudio() {
        return data != null && data.message != null && 
               (data.message.audioMessage != null || data.message.voiceMessage != null);
    }

    public boolean hasDocument() {
        return data != null && data.message != null && data.message.documentMessage != null;
    }

    public boolean hasSticker() {
        return data != null && data.message != null && data.message.stickerMessage != null;
    }

    public String getBase64() {
        if (data != null && data.message != null) return data.message.base64;
        return null;
    }
    
    public String getMimeType() {
        if (data == null || data.message == null) return null;
        if (data.message.imageMessage != null) return data.message.imageMessage.mimetype;
        if (data.message.audioMessage != null) return data.message.audioMessage.mimetype;
        if (data.message.voiceMessage != null) return data.message.voiceMessage.mimetype;
        if (data.message.documentMessage != null) return data.message.documentMessage.mimetype;
        if (data.message.stickerMessage != null) return "image/webp"; // Fallback padrão
        return null;
    }

    public String getMessageText() {
        if (data == null || data.message == null) return null;
        if (data.message.conversation != null && !data.message.conversation.isEmpty()) return data.message.conversation;
        if (data.message.extendedTextMessage != null && data.message.extendedTextMessage.text != null) return data.message.extendedTextMessage.text;
        if (data.message.imageMessage != null && data.message.imageMessage.caption != null) return data.message.imageMessage.caption;
        if (data.message.documentMessage != null && data.message.documentMessage.caption != null) return data.message.documentMessage.caption;
        if (data.message.listResponseMessage != null && data.message.listResponseMessage.singleSelectReply != null) return data.message.listResponseMessage.singleSelectReply.selectedRowId;
        if (data.message.buttonsResponseMessage != null) return data.message.buttonsResponseMessage.selectedButtonId;
        return null;
    }
    
    public String getPhoneNumber() {
        if (data != null && data.key != null && data.key.remoteJid != null) {
            return data.key.remoteJid.replace("@s.whatsapp.net", "");
        }
        return null;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataDTO {
        @JsonProperty("key") private KeyDTO key;
        @JsonProperty("pushName") private String pushName;
        @JsonProperty("message") private MessageDTO message;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeyDTO {
        @JsonProperty("remoteJid") private String remoteJid;
        @JsonProperty("fromMe") private Boolean fromMe;
        @JsonProperty("id") private String id;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageDTO {
        @JsonProperty("base64") private String base64; 
        @JsonProperty("conversation") private String conversation;
        @JsonProperty("extendedTextMessage") private ExtendedTextMessageDTO extendedTextMessage;
        @JsonProperty("imageMessage") private ImageMessageDTO imageMessage;
        @JsonProperty("audioMessage") private AudioMessageDTO audioMessage;
        @JsonProperty("voiceMessage") private AudioMessageDTO voiceMessage;
        @JsonProperty("documentMessage") private DocumentMessageDTO documentMessage;
        @JsonProperty("stickerMessage") private StickerMessageDTO stickerMessage;
        @JsonProperty("listResponseMessage") private ListResponseMessageDTO listResponseMessage;
        @JsonProperty("buttonsResponseMessage") private ButtonsResponseMessageDTO buttonsResponseMessage;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class ExtendedTextMessageDTO { @JsonProperty("text") private String text; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class ImageMessageDTO { @JsonProperty("caption") private String caption; @JsonProperty("mimetype") private String mimetype; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class AudioMessageDTO { @JsonProperty("mimetype") private String mimetype; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class DocumentMessageDTO { @JsonProperty("caption") private String caption; @JsonProperty("mimetype") private String mimetype; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class StickerMessageDTO { @JsonProperty("url") private String url; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class ListResponseMessageDTO { @JsonProperty("singleSelectReply") private SingleSelectReplyDTO singleSelectReply; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class SingleSelectReplyDTO { @JsonProperty("selectedRowId") private String selectedRowId; }
    @Data @JsonIgnoreProperties(ignoreUnknown = true) public static class ButtonsResponseMessageDTO { @JsonProperty("selectedButtonId") private String selectedButtonId; }
}