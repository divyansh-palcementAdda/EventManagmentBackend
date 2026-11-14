package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.WhatsAppOutbox;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WhatsAppOutboxRepository extends JpaRepository<WhatsAppOutbox, UUID> {

    @Query("SELECT o FROM WhatsAppOutbox o WHERE o.status = :status AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) ORDER BY o.createdAt ASC")
    List<WhatsAppOutbox> findPendingReadyForDelivery(
            @Param("status") WhatsAppOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    boolean existsByReferenceIdAndNotificationTypeAndStatusIn(
            String referenceId,
            WhatsAppNotificationType notificationType,
            List<WhatsAppOutboxStatus> statuses);
}
