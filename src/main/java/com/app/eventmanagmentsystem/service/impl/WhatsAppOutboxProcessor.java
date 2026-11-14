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
                item.setUpdatedAt(Instant.now());
                outboxRepository.save(item);

                WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                        .recipientMobile(item.getRecipientMobile())
                        .notificationType(item.getNotificationType())
                        .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                        .templateName(item.getTemplateName())
                        .providerMessageId(response.resolveMessageId())
                        .status("SENT")
                        .sentAt(Instant.now())
                        .build();
                messageLogRepository.save(logEntry);

                log.info("Successfully delivered WhatsApp {} message to {}",
                        item.getNotificationType(), item.getRecipientMobile());
            } else {
                handleDeliveryFailure(item, response != null && response.getError() != null
                        ? response.getError().getMessage() : "Provider rejected request");
            }
        } catch (Exception e) {
            handleDeliveryFailure(item, e.getMessage());
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

    private void handleDeliveryFailure(WhatsAppOutbox item, String errorMsg) {
        item.setAttemptCount(item.getAttemptCount() + 1);
        item.setLastError(errorMsg);

        if (item.getAttemptCount() >= item.getMaxAttempts()) {
            item.setStatus(WhatsAppOutboxStatus.FAILED);
            item.setUpdatedAt(Instant.now());

            WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                    .recipientMobile(item.getRecipientMobile())
                    .notificationType(item.getNotificationType())
                    .eventId(item.getEvent() != null ? item.getEvent().getId() : null)
                    .templateName(item.getTemplateName())
                    .status("FAILED")
                    .errorMessage(errorMsg)
                    .retryCount(item.getAttemptCount())
                    .failedAt(Instant.now())
                    .build();
            messageLogRepository.save(logEntry);

            log.warn("WhatsApp message {} permanently failed after {} attempts: {}",
                    item.getId(), item.getAttemptCount(), errorMsg);
        } else {
            // Exponential backoff: 2^attempt minutes
            long backoffMinutes = (long) Math.pow(2, item.getAttemptCount());
            item.setStatus(WhatsAppOutboxStatus.PENDING);
            item.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(backoffMinutes)));
            item.setUpdatedAt(Instant.now());

            log.info("WhatsApp message {} scheduled for retry in {} minutes (attempt {}/{})",
                    item.getId(), backoffMinutes, item.getAttemptCount(), item.getMaxAttempts());
        }

        outboxRepository.save(item);
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
