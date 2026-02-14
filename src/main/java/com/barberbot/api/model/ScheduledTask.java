package com.barberbot.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduled_tasks")
@Data // O Lombok gera o setUpdatedAt automaticamente por causa deste campo
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;
    
    @Column(name = "execution_time", nullable = false)
    private LocalDateTime executionTime;
    
    @Column(name = "task_type", nullable = false)
    private String taskType; // Ex: REMINDER, REVIEW_REQUEST
    
    @Column(name = "message_content", length = 1000)
    private String messageContent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;
    
    // --- CAMPOS QUE FALTAVAM ---
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    // ---------------------------
    
    public enum TaskStatus {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}