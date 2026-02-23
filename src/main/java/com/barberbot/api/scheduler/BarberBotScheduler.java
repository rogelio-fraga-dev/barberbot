package com.barberbot.api.scheduler;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.model.ScheduledTask;
import com.barberbot.api.repository.CustomerRepository;
import com.barberbot.api.repository.ScheduledTaskRepository;
import com.barberbot.api.service.CustomerService;
import com.barberbot.api.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BarberBotScheduler {

    private final ScheduledTaskRepository taskRepository;
    private final WhatsAppService whatsAppService;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final BarberBotProperties properties;

    /**
     * MOTOR DE DISPAROS (Roda a cada 60 segundos)
     * Verifica o banco e envia as mensagens na hora certa.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processScheduledTasks() {
        LocalDateTime now = LocalDateTime.now();
        
        // Busca tarefas que já deram o horário e ainda não foram enviadas
        List<ScheduledTask> pendingTasks = taskRepository.findByStatusAndExecutionTimeBefore(
                ScheduledTask.TaskStatus.PENDING, now
        );

        if (pendingTasks.isEmpty()) return;

        log.info("Processando {} tarefas pendentes...", pendingTasks.size());

        for (ScheduledTask task : pendingTasks) {
            try {
                String phone = task.getCustomerPhone();

                // Se o cliente estiver pausado (falando com o Luiz), adia o lembrete por 1 hora
                if (customerService.isCustomerPaused(phone)) {
                    log.info("Cliente {} em pausa. Adiando tarefa.", phone);
                    task.setExecutionTime(LocalDateTime.now().plusHours(1));
                    taskRepository.save(task);
                    continue;
                }

                whatsAppService.sendTextMessage(phone, task.getMessageContent());

                task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
                taskRepository.save(task);

            } catch (Exception e) {
                log.error("Erro na tarefa {}: {}", task.getId(), e.getMessage());
                task.setStatus(ScheduledTask.TaskStatus.FAILED);
                taskRepository.save(task);
            }
        }
    }

    /**
     * RELATÓRIO MATINAL (08:00) - Bom dia e Status do Sistema
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyAdminReport() {
        try {
            String adminPhone = properties.getAdmin().getPhone();
            long totalCustomers = customerRepository.count();
            
            String report = String.format("""
                    ☀️ *Bom dia, Luiz!*
                    
                    BarberBot online. 🤖✅
                    Base de Clientes: %d
                    
                    Tenha um excelente dia de trabalho! 💈
                    """, totalCustomers);

            whatsAppService.sendTextMessage(adminPhone, report);
            
        } catch (Exception e) {
            log.error("Erro ao enviar relatório matinal: {}", e.getMessage());
        }
    }

    /**
     * LEMBRETE NOTURNO DA AGENDA (21:00)
     * Cobra o Luiz para mandar a foto da agenda do dia seguinte.
     */
    @Scheduled(cron = "0 0 21 * * *")
    public void sendNightlyAgendaRequest() {
        try {
            String adminPhone = properties.getAdmin().getPhone();
            
            // Define o intervalo de "Amanhã"
            LocalDateTime startOfTomorrow = LocalDate.now().plusDays(1).atStartOfDay();
            LocalDateTime endOfTomorrow = LocalDate.now().plusDays(1).atTime(LocalTime.MAX);
            
            // Verifica se JÁ EXISTEM lembretes criados para amanhã
            List<ScheduledTask> tomorrowsTasks = taskRepository.findByExecutionTimeBetweenAndTaskType(
                startOfTomorrow, endOfTomorrow, "REMINDER"
            );
            
            StringBuilder message = new StringBuilder();
            
            if (tomorrowsTasks.isEmpty()) {
                // CENÁRIO 1: Luiz esqueceu de mandar a agenda. O Bot cobra!
                message.append("🌙 *Opa Luiz, boa noite!*\n\n");
                message.append("⚠️ Ainda não recebi a agenda de amanhã.\n\n");
                message.append("📸 *Mande a foto da agenda agora* para eu programar os lembretes dos clientes e garantir que ninguém falte!\n");
                message.append("\n_Estou aguardando..._");
            } else {
                // CENÁRIO 2: Luiz já mandou. O Bot confirma e mostra o resumo.
                message.append("🌙 *Resumo para Amanhã (Já programado)*\n\n");
                message.append("✅ A agenda já está no sistema! Vou enviar lembretes para:\n\n");
                
                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
                for (ScheduledTask task : tomorrowsTasks) {
                    // +1 hora porque o lembrete é enviado 1h antes do corte real
                    String corteTime = task.getExecutionTime().plusHours(1).format(timeFmt);
                    String phone = task.getCustomerPhone().length() > 4 ? 
                        task.getCustomerPhone().substring(task.getCustomerPhone().length() - 4) : "---";
                        
                    message.append("✂️ ").append(corteTime).append(" - Final ").append(phone).append("\n");
                }
                message.append("\nPode descansar que eu cuido dos avisos! 💤");
            }
            
            whatsAppService.sendTextMessage(adminPhone, message.toString());
            log.info("Lembrete noturno enviado para o admin.");
            
        } catch (Exception e) {
            log.error("Erro ao enviar lembrete noturno: {}", e.getMessage());
        }
    }
}