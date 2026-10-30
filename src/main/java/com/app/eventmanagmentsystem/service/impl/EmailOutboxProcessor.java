package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.EmailOutbox;
import com.app.eventmanagmentsystem.model.enums.EmailOutboxStatus;
import com.app.eventmanagmentsystem.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled email outbox processor.
 *
 * <p>Polls the email_outbox table for PENDING records whose next_retry_at is in the past.
 * Delivery is attempted asynchronously after the activation transaction commits.
 * Failure increments the attempt count and schedules a retry with exponential backoff.
 * After max attempts the record is marked FAILED — the pass remains activated.
 *
 * <p>SMTP credentials are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOutboxProcessor {

    private final EmailOutboxRepository outboxRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@eventpass.local}")
    private String fromAddress;

    @Value("${app.mail.outbox.batch-size:10}")
    private int batchSize;

    @Value("${app.mail.outbox.max-attempts:5}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.mail.outbox.poll-delay-ms:30000}")
    @Transactional
    public void processOutbox() {
        List<EmailOutbox> pending = outboxRepository.findPendingReadyForDelivery(
                EmailOutboxStatus.PENDING, Instant.now(), PageRequest.of(0, batchSize));

        for (EmailOutbox record : pending) {
            attemptDelivery(record);
        }
    }

    private void attemptDelivery(EmailOutbox record) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(record.getRecipientEmail());
            message.setSubject(buildSubject(record));
            message.setText(buildBody(record));
            mailSender.send(message);

            record.setStatus(EmailOutboxStatus.SENT);
            record.setSentAt(Instant.now());
            outboxRepository.save(record);
            log.info("Email sent to {} for type {}", record.getRecipientEmail(), record.getNotificationType());

        } catch (MailException e) {
            int attempts = record.getAttemptCount() + 1;
            record.setAttemptCount(attempts);
            record.setLastError(sanitizeError(e.getMessage()));

            if (attempts >= Math.min(record.getMaxAttempts(), maxAttempts)) {
                record.setStatus(EmailOutboxStatus.FAILED);
                log.warn("Email permanently failed for recipient {} after {} attempts",
                        record.getRecipientEmail(), attempts);
            } else {
                // Exponential backoff: 2^attempt minutes
                long backoffMinutes = (long) Math.pow(2, attempts);
                record.setNextRetryAt(Instant.now().plusSeconds(backoffMinutes * 60));
                log.warn("Email delivery failed (attempt {}), next retry in {} minutes",
                        attempts, backoffMinutes);
            }
            outboxRepository.save(record);
        }
    }

    private String buildSubject(EmailOutbox record) {
        return switch (record.getNotificationType()) {
            case PASS_ACTIVATION -> "Your Event Pass Has Been Activated";
            case PASS_REVOCATION -> "Your Event Pass Has Been Revoked";
            case EVENT_UPDATE -> "Event Update Notification";
            case CHECKIN_CONFIRMATION -> "Check-In Confirmation";
        };
    }

    private String buildBody(EmailOutbox record) {
        return switch (record.getNotificationType()) {
            case PASS_ACTIVATION ->
                    "Dear " + record.getRecipientName() + ",\n\n" +
                    "Your event pass has been successfully activated.\n" +
                    "Pass details: " + record.getPayload() + "\n\n" +
                    "We look forward to seeing you at the event!\n\nEventPass Team";
            case PASS_REVOCATION ->
                    "Dear " + record.getRecipientName() + ",\n\n" +
                    "Your event pass has been revoked. Please contact the event organizer for assistance.\n\nEventPass Team";
            default -> "Dear " + record.getRecipientName() + ",\n\nPlease see your event notification.\n\nEventPass Team";
        };
    }

    private String sanitizeError(String message) {
        if (message == null) return "Unknown error";
        // Truncate and strip any potential credential info
        String sanitized = message.replaceAll("(?i)(password|secret|credential|auth)[^\\s]*", "[REDACTED]");
        return sanitized.length() > 500 ? sanitized.substring(0, 497) + "..." : sanitized;
    }
}

