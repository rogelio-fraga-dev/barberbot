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
    
    /**
     * Processa uma agenda JSON e cria tarefas agendadas para envio de avaliações
     */
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
                    LocalDateTime executionTime = LocalDateTime.of(today, serviceTime)
                            .plusMinutes(properties.getSchedule().getDelayMinutes());
                    
                    // Mensagem padrão de avaliação
                    String reviewMessage = String.format(
                        "Olá %s! Esperamos que tenha gostado do seu atendimento hoje às %s. " +
                        "Sua opinião é muito importante para nós! " +
                        "Por favor, avalie nosso serviço: https://maps.app.goo.gl/seulink",
                        item.getName() != null ? item.getName() : "Cliente",
                        serviceTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    );
                    
                    ScheduledTask task = ScheduledTask.builder()
                            .customerPhone(item.getPhone())
                            .executionTime(executionTime)
                            .taskType("REVIEW_REQUEST")
                            .messageContent(reviewMessage)
                            .status(ScheduledTask.TaskStatus.PENDING)
                            .build();
                    
                    scheduledTaskRepository.save(task);
                    tasksCreated++;
                    log.info("Tarefa agendada para {} às {}", item.getPhone(), executionTime);
                    
                } catch (Exception e) {
                    log.error("Erro ao processar item da agenda: {}", item, e);
                }
            }
            
            log.info("Total de {} tarefas criadas a partir da agenda", tasksCreated);
            return tasksCreated;
            
        } catch (Exception e) {
            log.error("Erro ao processar agenda JSON: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar agenda", e);
        }
    }
    
    /**
     * Cria uma tarefa única de avaliação
     */
    @Transactional
    public ScheduledTask createReviewTask(String customerPhone, LocalDateTime serviceTime, String customerName) {
        LocalDateTime executionTime = serviceTime.plusMinutes(properties.getSchedule().getDelayMinutes());
        
        String reviewMessage = String.format(
            "Olá %s! Esperamos que tenha gostado do seu atendimento. " +
            "Sua opinião é muito importante para nós! " +
            "Por favor, avalie nosso serviço: https://maps.app.goo.gl/seulink",
            customerName != null ? customerName : "Cliente"
        );
        
        ScheduledTask task = ScheduledTask.builder()
                .customerPhone(customerPhone)
                .executionTime(executionTime)
                .taskType("REVIEW_REQUEST")
                .messageContent(reviewMessage)
                .status(ScheduledTask.TaskStatus.PENDING)
                .build();
        
        return scheduledTaskRepository.save(task);
    }
}
