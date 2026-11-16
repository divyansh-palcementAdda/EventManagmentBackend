package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.WhatsAppMessageLog;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLog, UUID>, JpaSpecificationExecutor<WhatsAppMessageLog> {

    Page<WhatsAppMessageLog> findByEventIdOrderByCreatedAtDesc(UUID eventId, Pageable pageable);

    Page<WhatsAppMessageLog> findByRecipientMobileContainingOrderByCreatedAtDesc(String recipientMobile, Pageable pageable);

    boolean existsByRecipientMobileAndNotificationTypeAndStatus(
            String recipientMobile,
            WhatsAppNotificationType notificationType,
            String status);

    boolean existsByPassIdAndStatus(UUID passId, String status);

    java.util.Optional<WhatsAppMessageLog> findByProviderMessageId(String providerMessageId);
}
