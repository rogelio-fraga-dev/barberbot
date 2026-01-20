package com.barberbot.api.repository;

import com.barberbot.api.model.ScheduledTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, UUID> {
    @Query("SELECT t FROM ScheduledTask t WHERE t.status = 'PENDING' AND t.executionTime <= :now ORDER BY t.executionTime ASC")
    List<ScheduledTask> findPendingTasksReadyToExecute(@Param("now") LocalDateTime now);
    
    List<ScheduledTask> findByCustomerPhone(String customerPhone);
    
    List<ScheduledTask> findByStatus(ScheduledTask.TaskStatus status);
}
