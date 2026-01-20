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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;
    
    @Column(name = "execution_time", nullable = false)
    private LocalDateTime executionTime;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;
    
    @Column(name = "task_type", nullable = false, length = 50)
    @Builder.Default
    private String taskType = "REVIEW_REQUEST";
    
    @Column(name = "message_content", columnDefinition = "TEXT")
    private String messageContent;
    
    @Column(name = "attempts")
    @Builder.Default
    private Integer attempts = 0;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    public enum TaskStatus {
        PENDING, COMPLETED, FAILED
    }
}
