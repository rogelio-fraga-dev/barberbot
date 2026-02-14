package com.barberbot.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Gera automaticamente getNumber(), setNumber(), getText(), setText()
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    
    // É crucial que os nomes sejam "number" e "text" para o Lombok gerar getNumber() e getText()
    private String number;
    private String text;
    
    // Campos opcionais úteis para o futuro
    private String mediaUrl;
    private String mediaType; // image, video, audio
    private int delay;
}