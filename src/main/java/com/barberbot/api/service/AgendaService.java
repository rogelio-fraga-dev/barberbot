package com.barberbot.api.service;

import com.barberbot.api.model.Customer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AgendaService {

    private final CustomerService customerService;
    private final WhatsAppService whatsAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Objeto Interno para guardar as informações exatas do agendamento
    @Data
    public static class Agendamento {
        private String name;
        private String phone;
        private String time;
        private String date;
        private String service;
        private boolean notified; // Trava para não mandar duas vezes
    }

    // A memória do robô para os cortes de hoje/amanhã
    private static final List<Agendamento> agendaAtiva = new ArrayList<>();
    private static final List<String> agendaDisplay = new ArrayList<>(); 

    public int processAgenda(String jsonAgenda) {
        int agendados = 0;
        agendaAtiva.clear();
        agendaDisplay.clear();

        try {
            JsonNode root = objectMapper.readTree(jsonAgenda);
            JsonNode items = root.get("items");

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String name = item.has("name") ? item.get("name").asText() : "";
                    String time = item.has("time") ? item.get("time").asText() : "";
                    String date = item.has("date") ? item.get("date").asText() : "Hoje";
                    String service = item.has("service") ? item.get("service").asText() : "Corte";

                    if (!name.isEmpty() && !time.isEmpty()) {
                        // Faz o Match com o CSV
                        Customer customer = customerService.findByName(name);
                        String phone = (customer != null) ? customer.getPhoneNumber() : null;
                        String phoneStatus = (phone != null) ? " (✅ Cadastrado)" : " (⚠️ Sem telefone na base)";
                        
                        // Guarda o agendamento estruturado
                        Agendamento ag = new Agendamento();
                        ag.setName(name);
                        ag.setPhone(phone);
                        ag.setTime(time);
                        ag.setDate(date);
                        ag.setService(service);
                        ag.setNotified(false); // Nasce como NÃO avisado
                        
                        agendaAtiva.add(ag);
                        agendaDisplay.add("📅 " + date + " às ⏰ " + time + " - " + name + " | " + service + phoneStatus);
                        
                        if (phone != null) agendados++;
                    }
                }
            }
            log.info("Agenda processada. {} clientes válidos para notificação.", agendados);
            return agendados;
        } catch (Exception e) {
            log.error("Erro Agenda JSON: {}", e.getMessage());
            throw new RuntimeException("Falha na agenda", e);
        }
    }
    
    public String getAgendaSalva() {
        if (agendaDisplay.isEmpty()) return "Nenhum agendamento lido. Me mande a foto da agenda primeiro!";
        return String.join("\n", agendaDisplay);
    }

    // ==============================================================
    // A MÁGICA: RODA A CADA 1 MINUTO OLHANDO O RELÓGIO
    // ==============================================================
    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")
    public void dispararLembretes1HoraAntes() {
        if (agendaAtiva.isEmpty()) return;

        LocalTime now = LocalTime.now(ZoneId.of("America/Sao_Paulo"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Agendamento appt : agendaAtiva) {
            // Se já avisou ou se não achou o telefone no CSV, ignora
            if (appt.isNotified() || appt.getPhone() == null) continue;

            try {
                // Limpa o horário caso a IA mande "09h00" ou "9:00"
                String cleanTime = appt.getTime().trim().replace("h", ":").replace(" ", "");
                if (cleanTime.length() == 4 && cleanTime.charAt(1) == ':') cleanTime = "0" + cleanTime;
                
                LocalTime apptTime = LocalTime.parse(cleanTime, formatter);
                
                // Calcula a diferença em minutos de agora até o horário do corte
                long minutesUntil = ChronoUnit.MINUTES.between(now, apptTime);
                
                // Se faltar exatamente entre 59 e 61 minutos (1 hora)
                if (minutesUntil <= 61 && minutesUntil >= 59) {
                    // Pega só o primeiro nome do cliente (Ex: "Adilson Martins" vira "Adilson")
                    String primeiroNome = appt.getName().split(" ")[0];
                    
                    String msg = String.format("Fala, %s! 💈\n\nPassando pra te lembrar do nosso horário agendado para daqui a pouco, às *%s* (%s).\n\nTe esperamos lá na LH Barbearia! ✂️🔥", 
                        primeiroNome, appt.getTime(), appt.getService());
                    
                    whatsAppService.sendTextMessage(appt.getPhone(), msg);
                    appt.setNotified(true); // Trava para não mandar de novo no próximo minuto
                    
                    log.info("[AGENDA] Lembrete de 1H enviado com sucesso para {} às {}", appt.getName(), appt.getTime());
                }
            } catch (Exception e) {
                log.error("[AGENDA-ERRO] Erro ao calcular horário para {}: {}", appt.getName(), e.getMessage());
            }
        }
    }
}