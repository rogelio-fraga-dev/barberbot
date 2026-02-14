package com.barberbot.api.scheduler;

import com.barberbot.api.config.BarberBotProperties;
import com.barberbot.api.model.ScheduledTask;
import com.barberbot.api.repository.ScheduledTaskRepository;
import com.barberbot.api.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component("barberBotTaskScheduler") // evita conflito com bean auto-configurado 'taskScheduler' do Spring Boot
@RequiredArgsConstructor
public class TaskScheduler {
    
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final WhatsAppService whatsAppService;
    private final BarberBotProperties properties;
    
    /**
     * Executa a cada 5 minutos para processar tarefas agendadas
     */
    @Scheduled(cron = "0 */5 * * * *") // A cada 5 minutos
    @Transactional
    public void processScheduledTasks() {
        log.info("Iniciando processamento de tarefas agendadas...");
        
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledTask> pendingTasks = scheduledTaskRepository
                .findPendingTasksReadyToExecute(now);
        
        if (pendingTasks.isEmpty()) {
            log.debug("Nenhuma tarefa pendente encontrada");
            return;
        }
        
        log.info("Encontradas {} tarefas pendentes para execução", pendingTasks.size());
        
        int batchSize = properties.getSchedule().getBatchSize();
        long delayBetweenMessages = properties.getSchedule().getDelayBetweenMessages();
        
        int processed = 0;
        for (ScheduledTask task : pendingTasks) {
            try {
                // Limita o tamanho do lote
                if (processed >= batchSize) {
                    log.info("Limite de lote atingido. Tarefas restantes serão processadas no próximo ciclo.");
                    break;
                }
                
                // Envia a mensagem
                whatsAppService.sendTextMessage(
                    task.getCustomerPhone(), 
                    task.getMessageContent()
                );
                
                // Marca como completa
                task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
                task.setCompletedAt(LocalDateTime.now());
                scheduledTaskRepository.save(task);
                
                processed++;
                log.info("Tarefa {} executada com sucesso para {}", 
                        task.getId(), task.getCustomerPhone());
                
                // Aguarda entre mensagens para evitar spam
                if (delayBetweenMessages > 0 && processed < pendingTasks.size()) {
                    try {
                        Thread.sleep(delayBetweenMessages);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrompida durante delay");
                        break;
                    }
                }
                
            } catch (Exception e) {
                log.error("Erro ao executar tarefa {}: {}", task.getId(), e.getMessage(), e);
                
                // Incrementa tentativas
                task.setAttempts(task.getAttempts() + 1);
                
                // Marca como falha após 3 tentativas
                if (task.getAttempts() >= 3) {
                    task.setStatus(ScheduledTask.TaskStatus.FAILED);
                    log.error("Tarefa {} marcada como FALHA após 3 tentativas", task.getId());
                }
                
                scheduledTaskRepository.save(task);
            }
        }
        
        log.info("Processamento concluído. {} tarefas processadas.", processed);
    }
}
