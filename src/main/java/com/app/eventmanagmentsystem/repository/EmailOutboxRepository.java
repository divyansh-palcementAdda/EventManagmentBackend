package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.EmailOutbox;
import com.app.eventmanagmentsystem.model.enums.EmailOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /**
     * Fetch PENDING records whose next_retry_at is in the past, limited by batchSize.
     */
    @Query(value = "SELECT e FROM EmailOutbox e WHERE e.status = :status AND e.nextRetryAt <= :now ORDER BY e.nextRetryAt ASC")
    List<EmailOutbox> findPendingReadyForDelivery(
            @Param("status") EmailOutboxStatus status,
            @Param("now") Instant now,
            org.springframework.data.domain.Pageable pageable);
}

