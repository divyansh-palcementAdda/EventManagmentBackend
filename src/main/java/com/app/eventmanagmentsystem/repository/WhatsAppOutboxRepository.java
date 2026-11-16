package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.WhatsAppOutbox;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppOutboxRepository extends JpaRepository<WhatsAppOutbox, UUID>, JpaSpecificationExecutor<WhatsAppOutbox> {

    @Query("SELECT o.id FROM WhatsAppOutbox o WHERE o.status IN (:statuses) AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) ORDER BY o.createdAt ASC")
    List<UUID> findCandidateIdsForDelivery(
            @Param("statuses") List<WhatsAppOutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);

    @Query("SELECT o FROM WhatsAppOutbox o WHERE o.status = :status AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) ORDER BY o.createdAt ASC")
    List<WhatsAppOutbox> findPendingReadyForDelivery(
            @Param("status") WhatsAppOutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE WhatsAppOutbox o SET o.status = :processingStatus, o.processingLeaseUntil = :leaseUntil, o.updatedAt = :now " +
           "WHERE o.id = :id AND o.status IN (:eligibleStatuses)")
    int claimRecord(
            @Param("id") UUID id,
            @Param("eligibleStatuses") List<WhatsAppOutboxStatus> eligibleStatuses,
            @Param("processingStatus") WhatsAppOutboxStatus processingStatus,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    @Query("SELECT o FROM WhatsAppOutbox o WHERE o.status = :status AND o.processingLeaseUntil IS NOT NULL AND o.processingLeaseUntil < :now")
    List<WhatsAppOutbox> findExpiredProcessing(
            @Param("status") WhatsAppOutboxStatus status,
            @Param("now") Instant now);

    boolean existsByReferenceIdAndNotificationType(
            String referenceId,
            WhatsAppNotificationType notificationType);

    boolean existsByReferenceIdAndNotificationTypeAndStatusIn(
            String referenceId,
            WhatsAppNotificationType notificationType,
            List<WhatsAppOutboxStatus> statuses);

    List<WhatsAppOutbox> findByReferenceIdAndNotificationType(
            String referenceId,
            WhatsAppNotificationType notificationType);

    Optional<WhatsAppOutbox> findByProviderMessageId(String providerMessageId);

    List<WhatsAppOutbox> findByStatus(WhatsAppOutboxStatus status);
}
