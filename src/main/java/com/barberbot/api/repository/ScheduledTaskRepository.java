package com.barberbot.api.repository;

import com.barberbot.api.model.ScheduledTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, UUID> {
    
    // Busca tarefas PENDENTES que já venceram (para o motor de disparo)
    List<ScheduledTask> findByStatusAndExecutionTimeBefore(
        ScheduledTask.TaskStatus status, 
        LocalDateTime now
    );
    
    // NOVO: Busca tarefas dentro de um intervalo (para o Relatório de Amanhã)
    List<ScheduledTask> findByExecutionTimeBetweenAndTaskType(
        LocalDateTime start, 
        LocalDateTime end,
        String taskType
    );
    
    List<ScheduledTask> findByCustomerPhone(String customerPhone);
}