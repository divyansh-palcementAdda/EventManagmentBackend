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

    private static final int BATCH_SIZE = 15;

    @Scheduled(fixedDelay = 20000)
    @Transactional
    public void processOutbox() {
        WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();

        // 1. If master integration is disabled, do nothing
        if (!settings.isIntegrationEnabled()) {
            return;
        }

        List<WhatsAppOutbox> pending = outboxRepository.findPendingReadyForDelivery(
                WhatsAppOutboxStatus.PENDING, Instant.now(), PageRequest.of(0, BATCH_SIZE));

        for (WhatsAppOutbox item : pending) {
            processItem(item, settings);
        }
    }

    private void processItem(WhatsAppOutbox item, WhatsAppNotificationSettings settings) {
        // Dynamic re-check of settings at execution time
        if (!settings.isIntegrationEnabled()) {
            item.setStatus(WhatsAppOutboxStatus.SUPPRESSED);
            item.setLastError("Suppressed: Master WhatsApp integration switch is disabled.");
            outboxRepository.save(item);
            logItemSuppressed(item, "Master switch OFF");
            return;
        }

        if (item.getNotificationType() == WhatsAppNotificationType.PASS_ACTIVATION && !settings.isPassActivationNotificationEnabled()) {
            item.setStatus(WhatsAppOutboxStatus.SUPPRESSED);
            item.setLastError("Suppressed: Pass activation notification toggle is disabled.");
            outboxRepository.save(item);
            logItemSuppressed(item, "Pass activation toggle OFF");
            return;
        }

        if (item.getNotificationType() == WhatsAppNotificationType.EVENT_REMINDER && !settings.isEventReminderEnabled()) {
            item.setStatus(WhatsAppOutboxStatus.SUPPRESSED);
            item.setLastError("Suppressed: Event reminder toggle is disabled.");
            outboxRepository.save(item);
            logItemSuppressed(item, "Event reminder toggle OFF");
            return;
        }

        if (item.getNotificationType() == WhatsAppNotificationType.OTP && !settings.isOtpVerificationEnabled()) {
            item.setStatus(WhatsAppOutboxStatus.SUPPRESSED);
            item.setLastError("Suppressed: OTP verification toggle is disabled.");
            outboxRepository.save(item);
            logItemSuppressed(item, "OTP toggle OFF");
            return;
        }

        // Deduplication check: Has a message already been successfully sent/accepted for this reference?
        if (hasAlreadyBeenSent(item)) {
            log.info("Outbox item {} ({}) already has confirmed SENT delivery — skipping duplicate dispatch",
                    item.getId(), item.getNotificationType());
            item.setStatus(WhatsAppOutboxStatus.SENT);
            item.setLastError("Marked as SENT: message was already successfully delivered.");
            item.setUpdatedAt(Instant.now());
            outboxRepository.save(item);
            return;
        }

        item.setStatus(WhatsAppOutboxStatus.PROCESSING);
        outboxRepository.save(item);

        try {
            Map<String, Object> payloadMap = Collections.emptyMap();
            if (item.getPayload() != null && !item.getPayload().isBlank()) {
                payloadMap = objectMapper.readValue(item.getPayload(), new TypeReference<>() {});
            }

            BotsenseMessageResponse response = dispatchMessage(item, payloadMap, settings);

            if (response != null && response.getError() == null) {
                // Success
                item.setStatus(WhatsAppOutboxStatus.SENT);
                item.setLastError(null);
                item.setUpdatedAt(Instant.now());
                outboxRepository.save(item);

                UUID passUuid = parseUuidSafely(item.getReferenceId());

                WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                        .recipientMobile(item.getRecipientMobile())
                        .notificationType(item.getNotificationType())
                        .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                        .passId(passUuid)
                        .templateName(item.getTemplateName())
                        .providerMessageId(response.resolveMessageId())
                        .status("SENT")
                        .sentAt(Instant.now())
                        .build();
                messageLogRepository.save(logEntry);

                log.info("Successfully delivered WhatsApp {} message to {}",
                        item.getNotificationType(), item.getRecipientMobile());
            } else {
                boolean isPermanent = isPermanentFailure(response, null);
                String errorMsg = response != null && response.getError() != null
                        ? response.getError().getMessage() : "Provider rejected request";
                handleDeliveryFailure(item, errorMsg, isPermanent);
            }
        } catch (Exception e) {
            boolean isPermanent = isPermanentFailure(null, e);
            handleDeliveryFailure(item, e.getMessage(), isPermanent);
        }
    }

    private BotsenseMessageResponse dispatchMessage(WhatsAppOutbox item, Map<String, Object> payload,
                                                    WhatsAppNotificationSettings settings) {
        String to = item.getRecipientMobile();

        if (item.getNotificationType() == WhatsAppNotificationType.PASS_ACTIVATION) {
            String template = settings.getPassActivationTemplateName();
            String lang = settings.getPassActivationTemplateLanguage();

            String attendeeName = (String) payload.getOrDefault("attendeeName", "Guest");
            String passNumber = (String) payload.getOrDefault("passNumber", "");
            String eventName = (String) payload.getOrDefault("eventName", "Event");

            List<BotsenseMessageRequest.Component> components = new ArrayList<>();
            components.add(BotsenseMessageRequest.Component.builder()
                    .type("body")
                    .parameters(List.of(
                            BotsenseMessageRequest.Parameter.builder().type("text").text(attendeeName).build(),
                            BotsenseMessageRequest.Parameter.builder().type("text").text(passNumber).build(),
                            BotsenseMessageRequest.Parameter.builder().type("text").text(eventName).build()
                    ))
                    .build());

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

    private void handleDeliveryFailure(WhatsAppOutbox item, String errorMsg, boolean isPermanent) {
        item.setAttemptCount(item.getAttemptCount() + 1);
        item.setLastError(errorMsg);
        item.setUpdatedAt(Instant.now());

        if (isPermanent || item.getAttemptCount() >= item.getMaxAttempts()) {
            item.setStatus(WhatsAppOutboxStatus.FAILED);
            item.setNextRetryAt(null);

            UUID passUuid = parseUuidSafely(item.getReferenceId());

            WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                    .recipientMobile(item.getRecipientMobile())
                    .notificationType(item.getNotificationType())
                    .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                    .passId(passUuid)
                    .templateName(item.getTemplateName())
                    .status("FAILED")
                    .errorMessage(errorMsg)
                    .retryCount(item.getAttemptCount())
                    .failedAt(Instant.now())
                    .build();
            messageLogRepository.save(logEntry);

            if (isPermanent) {
                log.warn("WhatsApp message {} marked permanently FAILED (non-retryable payload/client validation error): {}",
                        item.getId(), errorMsg);
            } else {
                log.warn("WhatsApp message {} permanently FAILED after reaching max attempts ({}): {}",
                        item.getId(), item.getAttemptCount(), errorMsg);
            }
        } else {
            // Transient error: Exponential backoff: 2^attempt minutes
            long backoffMinutes = (long) Math.pow(2, item.getAttemptCount());
            item.setStatus(WhatsAppOutboxStatus.PENDING);
            item.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(backoffMinutes)));

            log.info("WhatsApp message {} scheduled for transient retry in {} minutes (attempt {}/{}) due to: {}",
                    item.getId(), backoffMinutes, item.getAttemptCount(), item.getMaxAttempts(), errorMsg);
        }

        outboxRepository.save(item);
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
     * Safely re-queues failed outbox messages for delivery after payload/template correction,
     * verifying that no message has already been accepted/delivered to prevent duplicate dispatches.
     */
    @Transactional
    public int retryFailedMessages() {
        List<WhatsAppOutbox> failedItems = outboxRepository.findByStatus(WhatsAppOutboxStatus.FAILED);
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
