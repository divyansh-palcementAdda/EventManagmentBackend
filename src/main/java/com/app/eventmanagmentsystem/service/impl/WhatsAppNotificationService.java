package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import com.app.eventmanagmentsystem.repository.WhatsAppOutboxRepository;
import com.app.eventmanagmentsystem.service.BotsenseApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppNotificationService {

    private final WhatsAppSettingsService settingsService;
    private final WhatsAppOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Enqueue a Pass Activation confirmation message.
     * Checks master switch + pass activation toggle.
     */
    @Transactional
    public void enqueuePassActivation(EventPass pass, Attendee attendee) {
        WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();
        if (!settings.isIntegrationEnabled() || !settings.isPassActivationNotificationEnabled()) {
            log.debug("Pass activation WhatsApp notification skipped (Integration: {}, Toggle: {})",
                    settings.isIntegrationEnabled(), settings.isPassActivationNotificationEnabled());
            return;
        }

        String mobile = attendee.getMobileNumber();
        if (mobile == null || mobile.isBlank()) {
            log.debug("Attendee {} has no mobile number for WhatsApp pass activation", attendee.getId());
            return;
        }

        // Atomic deduplication: Check if an outbox record already exists for this pass
        boolean alreadyQueued = outboxRepository.existsByReferenceIdAndNotificationType(
                pass.getId().toString(),
                WhatsAppNotificationType.PASS_ACTIVATION
        );
        if (alreadyQueued) {
            log.info("Pass activation WhatsApp notification already queued/sent for pass {} — skipping duplicate", pass.getId());
            return;
        }

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("passId", pass.getId().toString());
            payloadMap.put("passNumber", pass.getPassNumber());
            payloadMap.put("category", pass.getCategory());
            payloadMap.put("eventId", pass.getEvent() != null ? pass.getEvent().getId().toString() : "");
            payloadMap.put("eventName", pass.getEvent() != null ? pass.getEvent().getName() : "");
            payloadMap.put("venue", pass.getEvent() != null ? pass.getEvent().getVenue() : "");
            payloadMap.put("attendeeName", attendee.getFullName());

            WhatsAppOutbox outbox = WhatsAppOutbox.builder()
                    .recipientMobile(BotsenseApiClient.formatPhoneNumber(mobile))
                    .recipientName(attendee.getFullName())
                    .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                    .referenceId(pass.getId().toString())
                    .event(pass.getEvent())
                    .templateName(settings.getPassActivationTemplateName())
                    .payload(objectMapper.writeValueAsString(payloadMap))
                    .status(WhatsAppOutboxStatus.PENDING)
                    .maxAttempts(5)
                    .build();

            try {
                outboxRepository.save(outbox);
                log.info("Enqueued pass activation WhatsApp message for pass {} to {}", pass.getPassNumber(), mobile);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.info("Concurrent activation enqueue detected for pass {} — benign duplicate ignored", pass.getId());
            }
        } catch (Exception e) {
            log.error("Failed to enqueue pass activation WhatsApp notification for pass {}: {}", pass.getId(), e.getMessage());
        }
    }

    /**
     * Enqueue an Event Reminder message.
     * Checks master switch + reminder toggle.
     */
    @Transactional
    public void enqueueEventReminder(Event event, Attendee attendee, EventPass pass, int daysRemaining) {
        WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();
        if (!settings.isIntegrationEnabled() || !settings.isEventReminderEnabled()) {
            return;
        }

        String mobile = attendee.getMobileNumber();
        if (mobile == null || mobile.isBlank()) return;

        // Deduplication: only one reminder per pass per event
        boolean alreadyQueued = outboxRepository.existsByReferenceIdAndNotificationTypeAndStatusIn(
                pass.getId().toString(),
                WhatsAppNotificationType.EVENT_REMINDER,
                List.of(WhatsAppOutboxStatus.PENDING, WhatsAppOutboxStatus.PROCESSING, WhatsAppOutboxStatus.SENT)
        );
        if (alreadyQueued) {
            return;
        }

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("passId", pass.getId().toString());
            payloadMap.put("passNumber", pass.getPassNumber());
            payloadMap.put("eventId", event.getId().toString());
            payloadMap.put("eventName", event.getName());
            payloadMap.put("venue", event.getVenue() != null ? event.getVenue() : "Official Venue");
            payloadMap.put("startsAt", event.getStartsAt() != null ? event.getStartsAt().toString() : "");
            payloadMap.put("daysRemaining", String.valueOf(daysRemaining));
            payloadMap.put("attendeeName", attendee.getFullName());

            WhatsAppOutbox outbox = WhatsAppOutbox.builder()
                    .recipientMobile(BotsenseApiClient.formatPhoneNumber(mobile))
                    .recipientName(attendee.getFullName())
                    .notificationType(WhatsAppNotificationType.EVENT_REMINDER)
                    .referenceId(pass.getId().toString())
                    .event(event)
                    .templateName(settings.getEventReminderTemplateName())
                    .payload(objectMapper.writeValueAsString(payloadMap))
                    .status(WhatsAppOutboxStatus.PENDING)
                    .maxAttempts(3)
                    .build();

            outboxRepository.save(outbox);
            log.info("Enqueued WhatsApp reminder for attendee {} (pass {}) for event {}",
                    attendee.getFullName(), pass.getPassNumber(), event.getName());
        } catch (Exception e) {
            log.error("Failed to enqueue WhatsApp event reminder for attendee {}: {}", attendee.getId(), e.getMessage());
        }
    }
}
