package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.WhatsAppMessageLog;
import com.app.eventmanagmentsystem.model.WhatsAppNotificationSettings;
import com.app.eventmanagmentsystem.model.WhatsAppOutbox;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseMessageRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseMessageResponse;
import com.app.eventmanagmentsystem.repository.WhatsAppMessageLogRepository;
import com.app.eventmanagmentsystem.repository.WhatsAppOutboxRepository;
import com.app.eventmanagmentsystem.service.BotsenseApiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppOutboxProcessor {

    private final WhatsAppOutboxRepository outboxRepository;
    private final WhatsAppMessageLogRepository messageLogRepository;
    private final WhatsAppSettingsService settingsService;
    private final BotsenseApiClient botsenseApiClient;
    private final ObjectMapper objectMapper;
    private final PassImageService passImageService;
    private final com.app.eventmanagmentsystem.config.BotsenseProperties botsenseProperties;

    private static final int BATCH_SIZE = 15;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    /**
     * Scheduled processor running every 20 seconds.
     * Note: processOutbox is intentionally NOT annotated with @Transactional
     * so that external HTTP calls to Botsense do NOT hold open database transactions.
     */
    @Scheduled(fixedDelayString = "${app.whatsapp.outbox.poll-delay-ms:20000}", initialDelayString = "${app.whatsapp.outbox.initial-delay-ms:20000}")
    public void processOutbox() {
        WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();

        // 1. If master integration is disabled, do nothing
        if (!settings.isIntegrationEnabled()) {
            return;
        }

        // 2. Recover expired PROCESSING leases (transition to UNKNOWN to prevent blind resend)
        recoverExpiredProcessingLeases();

        // 3. Reconcile any UNKNOWN records that have been waiting
        reconcileUnknownRecords();

        // 4. Find candidate IDs ready for delivery: PENDING or RETRY_WAIT where nextRetryAt <= now
        Instant now = Instant.now();
        List<UUID> candidateIds = outboxRepository.findCandidateIdsForDelivery(
                List.of(WhatsAppOutboxStatus.PENDING, WhatsAppOutboxStatus.RETRY_WAIT),
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (UUID id : candidateIds) {
            // Atomic conditional update to claim the record with a lease
            Instant leaseUntil = now.plus(LEASE_DURATION);
            int claimed = claimRecordAtomically(id, leaseUntil, now);

            if (claimed > 0) {
                // Worker successfully claimed this record — process without holding DB lock
                processClaimedItem(id, settings);
            }
        }
    }

    @Transactional
    public int claimRecordAtomically(UUID id, Instant leaseUntil, Instant now) {
        return outboxRepository.claimRecord(
                id,
                List.of(WhatsAppOutboxStatus.PENDING, WhatsAppOutboxStatus.RETRY_WAIT),
                WhatsAppOutboxStatus.PROCESSING,
                leaseUntil,
                now
        );
    }

    /**
     * Recovers records stuck in PROCESSING whose lease has expired.
     * An expired PROCESSING lease indicates the request may have already been transmitted
     * to Botsense before the worker crashed/stalled. Therefore, it is transitioned to UNKNOWN
     * rather than PENDING to prevent duplicate dispatches.
     */
    @Transactional
    public void recoverExpiredProcessingLeases() {
        Instant now = Instant.now();
        List<WhatsAppOutbox> expired = outboxRepository.findExpiredProcessing(
                WhatsAppOutboxStatus.PROCESSING, now);

        for (WhatsAppOutbox item : expired) {
            log.warn("Outbox record {} has expired PROCESSING lease (leaseUntil={}). Request may have been transmitted. Marking UNKNOWN.",
                    item.getId(), item.getProcessingLeaseUntil());
            item.setStatus(WhatsAppOutboxStatus.UNKNOWN);
            item.setUnknownOutcomeAt(now);
            item.setLastErrorCategory("LEASE_EXPIRED");
            item.setLastError("Worker lease expired while PROCESSING; transmission outcome unknown. Requires reconciliation/review.");
            item.setProcessingLeaseUntil(null);
            item.setUpdatedAt(now);
            outboxRepository.save(item);
        }
    }

    /**
     * Periodic reconciliation for UNKNOWN records:
     * Attempts to query provider or message log to determine whether the message was accepted.
     */
    public void reconcileUnknownRecords() {
        List<WhatsAppOutbox> unknowns = outboxRepository.findByStatus(WhatsAppOutboxStatus.UNKNOWN);
        for (WhatsAppOutbox item : unknowns) {
            // Only reconcile if it has been UNKNOWN for at least 30 seconds
            if (item.getUnknownOutcomeAt() != null &&
                    Duration.between(item.getUnknownOutcomeAt(), Instant.now()).getSeconds() >= 30) {
                if (item.getReconciliationAttempts() < 3) {
                    reconcileRecord(item);
                } else if (item.getStatus() == WhatsAppOutboxStatus.UNKNOWN) {
                    // Exhausted automated reconciliation attempts without confirmation -> Move to MANUAL_REVIEW
                    item.setStatus(WhatsAppOutboxStatus.MANUAL_REVIEW);
                    item.setReconciliationStatus("AMBIGUOUS");
                    item.setUpdatedAt(Instant.now());
                    outboxRepository.save(item);
                    log.warn("Outbox item {} to {} exhausted reconciliation attempts — moved to MANUAL_REVIEW",
                            item.getId(), item.getRecipientMobile());
                }
            }
        }
    }

    /**
     * Reconciles a single UNKNOWN outbox record.
     */
    @Transactional
    public void reconcileRecord(WhatsAppOutbox item) {
        item.setReconciliationAttempts(item.getReconciliationAttempts() + 1);
        item.setUpdatedAt(Instant.now());

        // 1. Check if message log already registered a successful delivery or webhook receipt
        if (hasAlreadyBeenSent(item)) {
            log.info("Reconciliation: Outbox item {} was confirmed delivered in message logs — marking SENT", item.getId());
            item.setStatus(WhatsAppOutboxStatus.SENT);
            item.setReconciliationStatus("ACCEPTED");
            item.setLastError("Reconciliation confirmed delivery from audit logs");
            outboxRepository.save(item);
            return;
        }

        // 2. Query provider by message ID if available
        if (item.getProviderMessageId() != null && !item.getProviderMessageId().isBlank()) {
            Optional<Map<String, Object>> statusOpt = botsenseApiClient.checkMessageStatus(item.getProviderMessageId());
            if (statusOpt.isPresent()) {
                Map<String, Object> statusMap = statusOpt.get();
                String provStatus = String.valueOf(statusMap.getOrDefault("status", "")).toUpperCase();
                if ("SENT".equals(provStatus) || "DELIVERED".equals(provStatus) || "READ".equals(provStatus)) {
                    item.setStatus("DELIVERED".equals(provStatus) ? WhatsAppOutboxStatus.DELIVERED : WhatsAppOutboxStatus.SENT);
                    item.setReconciliationStatus("ACCEPTED");
                    item.setLastError("Reconciliation confirmed by provider status: " + provStatus);
                    outboxRepository.save(item);
                    return;
                } else if ("FAILED".equals(provStatus)) {
                    item.setStatus(WhatsAppOutboxStatus.RETRY_WAIT);
                    item.setReconciliationStatus("NOT_ACCEPTED");
                    item.setNextRetryAt(calculateBackoff(item.getAttemptCount()));
                    item.setLastError("Provider confirmed failure: " + statusMap.get("error"));
                    outboxRepository.save(item);
                    return;
                }
            }
        }

        // 3. Query provider by recipient mobile number
        Optional<Map<String, Object>> recipientQueryOpt = botsenseApiClient.checkMessageByRecipient(item.getRecipientMobile());
        if (recipientQueryOpt.isPresent()) {
            Map<String, Object> queryMap = recipientQueryOpt.get();
            Object dataObj = queryMap.get("data");
            if (dataObj instanceof List && !((List<?>) dataObj).isEmpty()) {
                log.info("Reconciliation: Provider confirmed message for {} — marking SENT", item.getRecipientMobile());
                item.setStatus(WhatsAppOutboxStatus.SENT);
                item.setReconciliationStatus("ACCEPTED");
                item.setLastError("Reconciliation confirmed message in provider recipient history");
                outboxRepository.save(item);
                return;
            }
        }

        // 4. Inconclusive: If provider does not confirm, remain in UNKNOWN or MANUAL_REVIEW
        if (item.getReconciliationAttempts() >= 3) {
            item.setStatus(WhatsAppOutboxStatus.MANUAL_REVIEW);
            item.setReconciliationStatus("AMBIGUOUS");
            item.setLastError("Reconciliation inconclusive: provider query unsupported or returned no matching records. Admin review required.");
            log.warn("Reconciliation inconclusive for item {} to {} — moved to MANUAL_REVIEW", item.getId(), item.getRecipientMobile());
        } else {
            item.setReconciliationStatus("UNRECONCILED");
        }
        outboxRepository.save(item);
    }

    /**
     * Process an item that has been claimed atomically.
     */
    public void processClaimedItem(UUID id, WhatsAppNotificationSettings settings) {
        WhatsAppOutbox item = outboxRepository.findById(id).orElse(null);
        if (item == null) {
            return;
        }

        // Dynamic re-check of settings at execution time
        if (!settings.isIntegrationEnabled()) {
            suppressItem(item, "Master switch OFF");
            return;
        }

        if (item.getNotificationType() == WhatsAppNotificationType.PASS_ACTIVATION && !settings.isPassActivationNotificationEnabled()) {
            suppressItem(item, "Pass activation toggle OFF");
            return;
        }

        if (item.getNotificationType() == WhatsAppNotificationType.EVENT_REMINDER && !settings.isEventReminderEnabled()) {
            suppressItem(item, "Event reminder toggle OFF");
            return;
        }

        if (item.getNotificationType() == WhatsAppNotificationType.OTP && !settings.isOtpVerificationEnabled()) {
            suppressItem(item, "OTP toggle OFF");
            return;
        }

        // Deduplication check: Has a message already been successfully sent/accepted for this reference?
        if (hasAlreadyBeenSent(item)) {
            log.info("Outbox item {} ({}) already has confirmed SENT delivery — skipping duplicate dispatch",
                    item.getId(), item.getNotificationType());
            item.setStatus(WhatsAppOutboxStatus.SENT);
            item.setProcessingLeaseUntil(null);
            item.setLastError("Marked as SENT: message was already successfully delivered.");
            item.setUpdatedAt(Instant.now());
            outboxRepository.save(item);
            return;
        }

        // Record dispatch attempt
        item.setLastDispatchAt(Instant.now());
        item.setAttemptCount(item.getAttemptCount() + 1);

        try {
            Map<String, Object> payloadMap = Collections.emptyMap();
            if (item.getPayload() != null && !item.getPayload().isBlank()) {
                payloadMap = objectMapper.readValue(item.getPayload(), new TypeReference<>() {});
            }

            BotsenseMessageResponse response = dispatchMessage(item, payloadMap, settings);

            if (response != null && response.getError() == null) {
                // Case A: Confirmed Successful HTTP response
                handleSuccess(item, response);
            } else {
                handleFailureResponse(item, response, null);
            }
        } catch (Exception e) {
            handleFailureResponse(item, null, e);
        }
    }

    private void handleSuccess(WhatsAppOutbox item, BotsenseMessageResponse response) {
        item.setStatus(WhatsAppOutboxStatus.SENT);
        item.setProviderMessageId(response != null ? response.resolveMessageId() : null);
        item.setLastError(null);
        item.setLastErrorCategory(null);
        item.setProcessingLeaseUntil(null);
        item.setUpdatedAt(Instant.now());
        outboxRepository.save(item);

        UUID passUuid = parseUuidSafely(item.getReferenceId());

        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                .recipientMobile(item.getRecipientMobile())
                .notificationType(item.getNotificationType())
                .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                .passId(passUuid)
                .templateName(item.getTemplateName())
                .providerMessageId(item.getProviderMessageId())
                .status("SENT")
                .sentAt(Instant.now())
                .build();
        messageLogRepository.save(logEntry);

        log.info("Successfully delivered WhatsApp {} message to {}",
                item.getNotificationType(), item.getRecipientMobile());
    }

    private void handleFailureResponse(WhatsAppOutbox item, BotsenseMessageResponse response, Exception ex) {
        Instant now = Instant.now();
        item.setProcessingLeaseUntil(null);
        item.setUpdatedAt(now);

        String errorType = "";
        String errorCode = "";
        String errorMsg = "";
        Integer httpStatus = null;

        if (response != null && response.getError() != null) {
            BotsenseMessageResponse.ErrorDetails err = response.getError();
            errorType = err.getType() != null ? err.getType().toUpperCase() : "";
            errorCode = err.getCode() != null ? err.getCode().toUpperCase() : "";
            errorMsg = err.getMessage() != null ? err.getMessage() : "Provider rejected request";
            httpStatus = err.getHttpStatus();
        } else if (ex != null) {
            errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }

        // =========================================================================
        // Case B: READ TIMEOUT (Ambiguous Delivery Outcome)
        // Request was transmitted; waiting for response timed out.
        // DO NOT AUTOMATICALLY RETRY! Mark UNKNOWN.
        // =========================================================================
        if ("READ_TIMEOUT".equals(errorCode) || "READ_TIMEOUT".equals(errorType)
                || errorMsg.toLowerCase().contains("read timed out")
                || (httpStatus != null && httpStatus == 504)) {

            item.setStatus(WhatsAppOutboxStatus.UNKNOWN);
            item.setUnknownOutcomeAt(now);
            item.setLastErrorCategory("READ_TIMEOUT");
            item.setLastError("Read timed out waiting for Botsense response; delivery outcome is unknown. Auto-retry suspended.");
            item.setNextRetryAt(null);
            outboxRepository.save(item);

            UUID passUuid = parseUuidSafely(item.getReferenceId());
            WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                    .recipientMobile(item.getRecipientMobile())
                    .notificationType(item.getNotificationType())
                    .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                    .passId(passUuid)
                    .templateName(item.getTemplateName())
                    .status("UNKNOWN")
                    .errorCode("READ_TIMEOUT")
                    .errorMessage("Read timed out waiting for Botsense response; delivery outcome unknown.")
                    .retryCount(item.getAttemptCount())
                    .build();
            messageLogRepository.save(logEntry);

            log.warn("WhatsApp outbox item {} to {} resulted in READ TIMEOUT. Marked UNKNOWN to prevent duplicate delivery.",
                    item.getId(), item.getRecipientMobile());
            return;
        }

        // =========================================================================
        // Case C: CONNECT FAILURE (Connection failed BEFORE request transmission)
        // Confirmed transient failure to send; safe to retry.
        // =========================================================================
        if ("CONNECT_FAILURE".equals(errorCode) || "CONNECT_FAILURE".equals(errorType)
                || errorMsg.toLowerCase().contains("connection refused")
                || errorMsg.toLowerCase().contains("connect timed out")) {

            item.setLastErrorCategory("CONNECT_FAILURE");
            item.setLastError("Connection failed before transmission: " + errorMsg);

            if (item.getAttemptCount() >= item.getMaxAttempts()) {
                item.setStatus(WhatsAppOutboxStatus.FAILED_PERMANENTLY);
                item.setNextRetryAt(null);
                log.warn("WhatsApp message {} permanently FAILED after reaching max attempts ({}) on connect failure",
                        item.getId(), item.getAttemptCount());
            } else {
                item.setStatus(WhatsAppOutboxStatus.RETRY_WAIT);
                item.setNextRetryAt(calculateBackoff(item.getAttemptCount()));
                log.info("WhatsApp message {} scheduled for retry (attempt {}/{}) due to connect failure",
                        item.getId(), item.getAttemptCount(), item.getMaxAttempts());
            }
            outboxRepository.save(item);
            return;
        }

        // =========================================================================
        // Case D: HTTP 4xx Permanent Validation Errors (Except 429)
        // Invalid template, parameter mismatch, invalid number -> Non-retryable.
        // =========================================================================
        boolean isPermanent = isPermanentFailure(response, ex);
        if (isPermanent) {
            item.setStatus(WhatsAppOutboxStatus.FAILED_PERMANENTLY);
            item.setLastErrorCategory("VALIDATION_ERROR");
            item.setLastError(errorMsg);
            item.setNextRetryAt(null);
            outboxRepository.save(item);

            UUID passUuid = parseUuidSafely(item.getReferenceId());
            WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                    .recipientMobile(item.getRecipientMobile())
                    .notificationType(item.getNotificationType())
                    .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                    .passId(passUuid)
                    .templateName(item.getTemplateName())
                    .status("FAILED")
                    .errorCode(errorCode != null && !errorCode.isBlank() ? errorCode : "PERMANENT_ERROR")
                    .errorMessage(errorMsg)
                    .retryCount(item.getAttemptCount())
                    .failedAt(now)
                    .build();
            messageLogRepository.save(logEntry);

            log.warn("WhatsApp message {} permanently FAILED (non-retryable 4xx validation error): {}",
                    item.getId(), errorMsg);
            return;
        }

        // =========================================================================
        // Case E: HTTP 429 (Rate Limit) or Transient 5xx Server Errors
        // Bounded retries with exponential backoff and jitter.
        // =========================================================================
        String category = (httpStatus != null && httpStatus == 429) ? "RATE_LIMIT" : "SERVER_ERROR";
        item.setLastErrorCategory(category);
        item.setLastError(errorMsg);

        if (item.getAttemptCount() >= item.getMaxAttempts()) {
            item.setStatus(WhatsAppOutboxStatus.FAILED_PERMANENTLY);
            item.setNextRetryAt(null);
            log.warn("WhatsApp message {} permanently FAILED after reaching max attempts ({}) on {}",
                    item.getId(), item.getAttemptCount(), category);
        } else {
            item.setStatus(WhatsAppOutboxStatus.RETRY_WAIT);
            item.setNextRetryAt(calculateBackoff(item.getAttemptCount()));
            log.info("WhatsApp message {} scheduled for retry in {} (attempt {}/{}) due to {}",
                    item.getId(), item.getNextRetryAt(), item.getAttemptCount(), item.getMaxAttempts(), category);
        }
        outboxRepository.save(item);
    }

    private Instant calculateBackoff(int attemptCount) {
        // Exponential backoff: 2^attempt minutes (capped at 60 minutes) + jitter (0-30s)
        long baseMinutes = (long) Math.min(60, Math.pow(2, Math.max(1, attemptCount)));
        long jitterSeconds = (long) (Math.random() * 30);
        return Instant.now().plus(Duration.ofMinutes(baseMinutes)).plusSeconds(jitterSeconds);
    }

    private void suppressItem(WhatsAppOutbox item, String reason) {
        item.setStatus(WhatsAppOutboxStatus.SUPPRESSED);
        item.setProcessingLeaseUntil(null);
        item.setLastError("Suppressed at runtime: " + reason);
        item.setUpdatedAt(Instant.now());
        outboxRepository.save(item);
        logItemSuppressed(item, reason);
    }

    private BotsenseMessageResponse dispatchMessage(WhatsAppOutbox item, Map<String, Object> payload,
                                                    WhatsAppNotificationSettings settings) {
        String to = item.getRecipientMobile();

        if (item.getNotificationType() == WhatsAppNotificationType.PASS_ACTIVATION) {
            String template = settings.getPassActivationTemplateName();
            String lang = settings.getPassActivationTemplateLanguage();

            UUID passUuid = parseUuidSafely(item.getReferenceId());
            if (passUuid == null) {
                throw new IllegalStateException("Invalid pass reference ID: " + item.getReferenceId());
            }

            // Ensure the attendee-specific WhatsApp pass image is generated and saved on disk (forced refresh with activated attendee details)
            byte[] generatedImageBytes = passImageService.getOrGenerateWhatsAppPassImage(passUuid, true);
            if (generatedImageBytes == null || generatedImageBytes.length == 0) {
                log.error("WhatsApp pass image generation produced empty/null payload for pass ID [{}], outbox item [{}] - aborting dispatch",
                        passUuid, item.getId());
                throw new IllegalStateException("WhatsApp pass image generation failed for pass ID: " + passUuid);
            }

            // Construct supported media reference: prefer image.link with stable HTTPS public URL for WhatsApp pass image
            String publicLink = (String) payload.get("imageLink");
            if (publicLink == null || publicLink.isBlank() || publicLink.endsWith("/pass-image")) {
                publicLink = (String) payload.get("imageUrl");
            }
            if (publicLink == null || publicLink.isBlank() || publicLink.endsWith("/pass-image")) {
                String baseUrl = botsenseProperties.getMediaPublicBaseUrl();
                if (baseUrl == null || baseUrl.isBlank()) {
                    baseUrl = "https://event.areyoureporting.com";
                }
                baseUrl = baseUrl.replaceAll("/+$", "");
                publicLink = baseUrl + "/api/v1/passes/" + passUuid + "/whatsapp-pass-image";
            }

            BotsenseMessageRequest.MediaContent mediaContent;
            Object rawNumericId = payload.get("mediaNumericId");
            if (rawNumericId instanceof Number) {
                mediaContent = BotsenseMessageRequest.MediaContent.builder()
                        .id(((Number) rawNumericId).longValue())
                        .build();
                log.info("Using numeric media ID {} for pass activation image", rawNumericId);
            } else {
                mediaContent = BotsenseMessageRequest.MediaContent.builder()
                        .link(publicLink)
                        .build();
                log.info("Using stable HTTPS link for pass activation image: {}", publicLink);
            }

            // Construct header component with image parameter
            BotsenseMessageRequest.Component headerComponent = BotsenseMessageRequest.Component.builder()
                    .type("header")
                    .parameters(List.of(
                            BotsenseMessageRequest.Parameter.builder()
                                     .type("image")
                                    .image(mediaContent)
                                    .build()
                    ))
                    .build();

            List<BotsenseMessageRequest.Component> components = List.of(headerComponent);

            log.info("Dispatching WhatsApp template [{}] to {}: headerParams=1 (image.link={}), bodyParams=0",
                    template, to, mediaContent.getLink() != null ? mediaContent.getLink() : mediaContent.getId());
            return botsenseApiClient.sendTemplateMessage(to, template, lang, components);
        } else if (item.getNotificationType() == WhatsAppNotificationType.EVENT_REMINDER) {
            String template = settings.getEventReminderTemplateName();
            String lang = settings.getEventReminderTemplateLanguage();

            String daysRemaining = (String) payload.getOrDefault("daysRemaining", "1");
            String startsAt = (String) payload.getOrDefault("startsAt", "Soon");
            String venue = (String) payload.getOrDefault("venue", "Campus");

            List<BotsenseMessageRequest.Component> components = new ArrayList<>();
            components.add(BotsenseMessageRequest.Component.builder()
                    .type("body")
                    .parameters(List.of(
                            BotsenseMessageRequest.Parameter.builder().type("text").text(daysRemaining).build(),
                            BotsenseMessageRequest.Parameter.builder().type("text").text(startsAt).build(),
                            BotsenseMessageRequest.Parameter.builder().type("text").text(venue).build()
                    ))
                    .build());

            return botsenseApiClient.sendTemplateMessage(to, template, lang, components);
        }

        return botsenseApiClient.sendTextMessage(to, "Notification from EventPass");
    }

    public static boolean isPermanentFailure(BotsenseMessageResponse response, Exception ex) {
        if (response != null && response.getError() != null) {
            BotsenseMessageResponse.ErrorDetails error = response.getError();
            Integer httpStatus = error.getHttpStatus();

            // HTTP 4xx (except 429 rate limit) are permanent client/payload errors
            if (httpStatus != null) {
                if (httpStatus == 429) {
                    return false; // Transient rate limit
                }
                if (httpStatus >= 400 && httpStatus < 500) {
                    return true; // Permanent client/validation error
                }
            }

            String type = error.getType() != null ? error.getType().toLowerCase() : "";
            String code = error.getCode() != null ? error.getCode().toLowerCase() : "";
            String message = error.getMessage() != null ? error.getMessage().toLowerCase() : "";

            if (type.contains("invalid_request") || type.contains("validation")
                    || code.contains("invalid_request_body") || code.contains("bad_request")
                    || message.contains("invalid input") || message.contains("expected string")
                    || message.contains("not found") || message.contains("does not exist")
                    || message.contains("invalid parameter") || message.contains("parameter mismatch")) {
                return true;
            }
        }

        if (ex != null) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("400 bad request") || msg.contains("invalid input")
                    || msg.contains("expected string") || msg.contains("422 unprocessable")) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAlreadyBeenSent(WhatsAppOutbox item) {
        if (item.getReferenceId() != null && !item.getReferenceId().isBlank()) {
            UUID passUuid = parseUuidSafely(item.getReferenceId());
            if (passUuid != null && messageLogRepository.existsByPassIdAndStatus(passUuid, "SENT")) {
                return true;
            }
        }

        return messageLogRepository.existsByRecipientMobileAndNotificationTypeAndStatus(
                item.getRecipientMobile(), item.getNotificationType(), "SENT");
    }

    private UUID parseUuidSafely(String ref) {
        if (ref == null || ref.isBlank()) return null;
        try {
            return UUID.fromString(ref);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Permission-protected manual resend action for UNKNOWN or MANUAL_REVIEW outbox records.
     * Requires explicit confirmation that the operator understands the original message may
     * already have been delivered.
     */
    @Transactional
    public void manualResend(UUID outboxId, String operatorUsername) {
        WhatsAppOutbox item = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox record not found: " + outboxId));

        if (item.getStatus() != WhatsAppOutboxStatus.UNKNOWN &&
                item.getStatus() != WhatsAppOutboxStatus.MANUAL_REVIEW &&
                item.getStatus() != WhatsAppOutboxStatus.FAILED &&
                item.getStatus() != WhatsAppOutboxStatus.FAILED_PERMANENTLY) {
            throw new IllegalStateException("Only UNKNOWN, MANUAL_REVIEW, or FAILED records can be manually resent. Current status: " + item.getStatus());
        }

        log.info("Operator '{}' manually triggered resend for WhatsApp outbox record {} (recipient: {})",
                operatorUsername, item.getId(), item.getRecipientMobile());

        item.setStatus(WhatsAppOutboxStatus.PENDING);
        item.setNextRetryAt(Instant.now());
        item.setProcessingLeaseUntil(null);
        item.setLastError("Manual resend authorized by operator: " + operatorUsername);
        item.setUpdatedAt(Instant.now());
        outboxRepository.save(item);
    }

    /**
     * Safely re-queues failed outbox messages for delivery after payload/template correction,
     * verifying that no message has already been accepted/delivered to prevent duplicate dispatches.
     */
    @Transactional
    public int retryFailedMessages() {
        List<WhatsAppOutbox> failedItems = outboxRepository.findByStatus(WhatsAppOutboxStatus.FAILED_PERMANENTLY);
        failedItems.addAll(outboxRepository.findByStatus(WhatsAppOutboxStatus.FAILED));
        int requeuedCount = 0;
        Instant now = Instant.now();

        for (WhatsAppOutbox item : failedItems) {
            if (hasAlreadyBeenSent(item)) {
                log.info("Outbox item {} was already delivered/accepted — marking as SENT to prevent duplicate", item.getId());
                item.setStatus(WhatsAppOutboxStatus.SENT);
                item.setLastError("Marked as SENT: message was already successfully delivered.");
                item.setUpdatedAt(now);
                outboxRepository.save(item);
                continue;
            }

            item.setStatus(WhatsAppOutboxStatus.PENDING);
            item.setAttemptCount(0);
            item.setNextRetryAt(now);
            item.setProcessingLeaseUntil(null);
            item.setLastError("Re-queued for redelivery after template payload correction");
            item.setUpdatedAt(now);
            outboxRepository.save(item);
            requeuedCount++;
        }

        log.info("Re-queued {} previously failed WhatsApp messages for delivery", requeuedCount);
        return requeuedCount;
    }

    private void logItemSuppressed(WhatsAppOutbox item, String reason) {
        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                .recipientMobile(item.getRecipientMobile())
                .notificationType(item.getNotificationType())
                .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                .templateName(item.getTemplateName())
                .status("SUPPRESSED")
                .errorMessage("Suppressed at runtime: " + reason)
                .build();
        messageLogRepository.save(logEntry);
    }
}
