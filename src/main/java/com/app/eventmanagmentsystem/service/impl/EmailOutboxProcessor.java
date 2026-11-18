package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.EmailOutbox;
import com.app.eventmanagmentsystem.model.enums.EmailOutboxStatus;
import com.app.eventmanagmentsystem.model.enums.NotificationType;
import com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot;
import com.app.eventmanagmentsystem.repository.EmailOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled email outbox processor.
 *
 * <p>Polls the email_outbox table for PENDING records whose next_retry_at is in the past.
 * Delivery is attempted asynchronously after the activation transaction commits.
 * Failure increments the attempt count and schedules a retry with exponential backoff.
 * After max attempts the record is marked FAILED — the pass remains activated.
 *
 * <p>For pass activations, dispatches a rich HTML email with the original artwork
 * and dynamically composited QR and sequence number embedded via inline Content-ID (CID).
 *
 * <p>SMTP credentials are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOutboxProcessor {

    private final EmailOutboxRepository outboxRepository;
    private final JavaMailSender mailSender;
    private final RcfEmailPassService rcfEmailPassService;
    private final PassImageDataLoader passImageDataLoader;
    private final PassImageService passImageService;
    private final ObjectMapper objectMapper;

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
            boolean sent = false;

            // Pass Activation: Always send the rich pass email with artwork background & dynamic QR code
            if (record.getNotificationType() == NotificationType.PASS_ACTIVATION) {
                PassImageSnapshot snapshot = null;
                if (record.getReferenceId() != null) {
                    try {
                        UUID passId = UUID.fromString(record.getReferenceId());
                        snapshot = passImageDataLoader.loadSnapshot(passId);
                    } catch (Exception ex) {
                        log.warn("Could not load snapshot from referenceId {}: {}", record.getReferenceId(), ex.getMessage());
                    }
                }
                if (snapshot == null && record.getPayload() != null && !record.getPayload().isBlank()) {
                    snapshot = parseSnapshotFromPayload(record);
                }

                if (snapshot != null) {
                    sendPassActivationEmail(record, snapshot);
                    sent = true;
                } else {
                    log.error("Unable to obtain PassImageSnapshot for pass activation record {}. Triggering retry.", record.getId());
                    throw new IllegalStateException("Missing pass snapshot data for activation outbox record " + record.getId());
                }
            }

            if (!sent) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(record.getRecipientEmail());
                message.setSubject(buildSubject(record));
                message.setText(buildBody(record));
                mailSender.send(message);
            }

            record.setStatus(EmailOutboxStatus.SENT);
            record.setSentAt(Instant.now());
            outboxRepository.save(record);
            log.info("Email sent to {} for type {}", record.getRecipientEmail(), record.getNotificationType());

        } catch (Exception e) {
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
                log.warn("Email delivery failed (attempt {}), next retry in {} minutes: {}",
                        attempts, backoffMinutes, e.getMessage());
            }
            outboxRepository.save(record);
        }
    }

    private void sendPassActivationEmail(EmailOutbox record, PassImageSnapshot snapshot) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(record.getRecipientEmail());

        String passIdentifier = snapshot.getPassNumber() != null ? snapshot.getPassNumber() : snapshot.getPassId().toString();
        String eventName = snapshot.getEventName() != null && !snapshot.getEventName().isBlank()
                ? snapshot.getEventName() : "Renaissance Coffee Festival";
        helper.setSubject("Your " + eventName + " Entry Pass — " + passIdentifier);

        String activationUrl = passImageService.buildActivationUrl(passIdentifier);
        String htmlBody = rcfEmailPassService.renderEmailHtml(snapshot, activationUrl);
        helper.setText(htmlBody, true);

        // Add inline images with Content-ID matching cid: in the HTML body
        byte[] rcfPassImgBytes = rcfEmailPassService.renderRcfEmailPassImage(snapshot);
        helper.addInline("rcfPassImage", new ByteArrayResource(rcfPassImgBytes), "image/png");

        byte[] qrBytes = rcfEmailPassService.generateStandaloneQrPng(snapshot);
        helper.addInline("passQrCode", new ByteArrayResource(qrBytes), "image/png");

        mailSender.send(mimeMessage);
    }

    private PassImageSnapshot parseSnapshotFromPayload(EmailOutbox record) {
        try {
            JsonNode node = objectMapper.readTree(record.getPayload());
            String passNumber = node.has("passNumber") ? node.get("passNumber").asText() : null;
            String category = node.has("category") ? node.get("category").asText() : "STUDENT";
            String eventName = node.has("eventName") ? node.get("eventName").asText() : "Renaissance Coffee Festival";
            String attendeeName = node.has("attendeeName") ? node.get("attendeeName").asText() : record.getRecipientName();
            UUID passId = record.getReferenceId() != null ? UUID.fromString(record.getReferenceId()) : UUID.randomUUID();

            Long seqNum = null;
            if (passNumber != null && passNumber.contains("-")) {
                try {
                    String[] parts = passNumber.split("-");
                    seqNum = Long.parseLong(parts[parts.length - 1].replaceAll("\\D+", ""));
                } catch (Exception ignored) {}
            }

            return PassImageSnapshot.builder()
                    .passId(passId)
                    .passNumber(passNumber)
                    .sequenceNumber(seqNum)
                    .category(category)
                    .attendeeName(attendeeName)
                    .eventName(eventName)
                    .build();
        } catch (Exception e) {
            log.warn("Could not parse snapshot from outbox payload: {}", e.getMessage());
            return null;
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
                    "Your event pass has been successfully activated.\n\n" +
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

