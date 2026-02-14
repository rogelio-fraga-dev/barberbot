package com.barberbot.api.service;

import com.barberbot.api.dto.AgendaDTO;
import com.barberbot.api.model.ScheduledTask;
import com.barberbot.api.repository.ScheduledTaskRepository;
import com.barberbot.api.config.BarberBotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaService {
    
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final BarberBotProperties properties;
    private final ObjectMapper objectMapper;
    
    private static final String GOOGLE_REVIEW_LINK = "https://share.google/Vpq7gT3nMiz9Wl2Cj";
    
    @Transactional
    public int processAgenda(String agendaJson) {
        try {
            AgendaDTO agenda = objectMapper.readValue(agendaJson, AgendaDTO.class);
            
            if (agenda.getItems() == null || agenda.getItems().isEmpty()) {
                log.warn("Agenda vazia recebida");
                return 0;
            }
            
            int tasksCreated = 0;
            LocalDate today = LocalDate.now();
            
            for (AgendaDTO.AgendaItem item : agenda.getItems()) {
                try {
                    LocalTime serviceTime = item.getTime();
                    LocalDateTime appointmentDateTime = LocalDateTime.of(today, serviceTime);
                    String customerName = item.getName() != null ? item.getName() : "Campeão";
                    
                    // 1. LEMBRETE (1 hora antes)
                    LocalDateTime reminderTime = appointmentDateTime.minusHours(1);
                    if (reminderTime.isAfter(LocalDateTime.now())) {
                        String reminderMsg = String.format(
                            "Fala %s! 💈 Passando pra lembrar do seu horário hoje às %s na LH Barbearia. Estamos te esperando!",
                            customerName, serviceTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        );
                        scheduleTask(item.getPhone(), reminderTime, "REMINDER", reminderMsg);
                        tasksCreated++;
                    }

                    // 2. AVALIAÇÃO (Pós-corte, ex: 2h depois)
                    LocalDateTime reviewTime = appointmentDateTime.plusHours(2);
                    String reviewMsg = String.format(
                        "E aí %s, curtiu o visual novo? 🔥\nSua opinião fortalece demais nosso trabalho. Avalia a gente aqui rapidinho: %s",
                        customerName, GOOGLE_REVIEW_LINK
                    );
                    scheduleTask(item.getPhone(), reviewTime, "REVIEW_REQUEST", reviewMsg);
                    tasksCreated++;

                    // 3. REATIVAÇÃO RÁPIDA (Mudança para 10 DIAS)
                    // Como pedido: em vez de 25 dias, mandamos em 10 para manter o giro alto
                    LocalDateTime returnTime = appointmentDateTime.plusDays(10).withHour(9).withMinute(30); 
                    String returnMsg = String.format(
                        "Opa %s! 👊 Já faz 10 dias do último talento. Pra manter o corte sempre na régua, que tal já deixar agendado o próximo? ✂️\n\nClica aqui: %s",
                        customerName, properties.getMenu().getScheduleUrl()
                    );
                    scheduleTask(item.getPhone(), returnTime, "RETURN_REMINDER", returnMsg);
                    tasksCreated++;
                    
                } catch (Exception e) {
                    log.error("Erro ao processar item da agenda: {}", item, e);
                }
            }
            
            log.info("Processamento concluído. {} tarefas agendadas.", tasksCreated);
            return tasksCreated;
            
        } catch (Exception e) {
            log.error("Erro ao processar agenda JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar agenda", e);
        }
    }
    
    private void scheduleTask(String phone, LocalDateTime time, String type, String message) {
        ScheduledTask task = ScheduledTask.builder()
                .customerPhone(phone)
                .executionTime(time)
                .taskType(type)
                .messageContent(message)
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
        scheduledTaskRepository.save(task);
    }
}