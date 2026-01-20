package com.barberbot.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String phone;
    private String message;
    private String mediaUrl;
    private String mediaType; // "image", "audio", "document"
}
