package com.barberbot.api.repository;

import com.barberbot.api.model.Interaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    boolean existsByMessageId(String messageId);
    
    List<Interaction> findByCustomerIdOrderByTimestampDesc(UUID customerId);
    
    @Query("SELECT i FROM Interaction i WHERE i.customer.id = :customerId ORDER BY i.timestamp DESC")
    List<Interaction> findRecentInteractionsByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);
    
    default List<Interaction> findRecentInteractionsByCustomerId(UUID customerId) {
        return findRecentInteractionsByCustomerId(customerId, Pageable.ofSize(10));
    }
}
