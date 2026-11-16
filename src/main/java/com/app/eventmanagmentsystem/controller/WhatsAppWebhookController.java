package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.WhatsAppMessageLog;
import com.app.eventmanagmentsystem.model.WhatsAppOutbox;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import com.app.eventmanagmentsystem.repository.WhatsAppMessageLogRepository;
import com.app.eventmanagmentsystem.repository.WhatsAppOutboxRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Tag(name = "WhatsApp Webhook", description = "Webhook receiver for Botsense / WhatsApp Cloud API delivery receipts and status updates")
@RestController
@RequestMapping("/api/v1/whatsapp/webhook")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final WhatsAppOutboxRepository outboxRepository;
    private final WhatsAppMessageLogRepository logRepository;

    @Operation(summary = "Verify webhook subscription", description = "Meta/Botsense webhook verification handshake")
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(mode) && challenge != null) {
            log.info("WhatsApp webhook subscribed successfully");
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.ok("EventPass WhatsApp Webhook Ready");
    }

    @Operation(summary = "Handle webhook delivery receipts", description = "Processes delivered, read, sent, and failed status callbacks")
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received WhatsApp webhook event: {}", payload);

        try {
            processWebhookPayload(payload);
        } catch (Exception e) {
            log.warn("Error processing WhatsApp webhook payload: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @SuppressWarnings("unchecked")
    private void processWebhookPayload(Map<String, Object> payload) {
        if (!payload.containsKey("entry")) {
            return;
        }

        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) return;

        for (Map<String, Object> entry : entries) {
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null) continue;

            for (Map<String, Object> change : changes) {
                Map<String, Object> value = (Map<String, Object>) change.get("value");
                if (value == null) continue;

                List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.get("statuses");
                if (statuses == null) continue;

                for (Map<String, Object> statusItem : statuses) {
                    String messageId = (String) statusItem.get("id");
                    String status = String.valueOf(statusItem.get("status")).toLowerCase();
                    String recipientId = (String) statusItem.get("recipient_id");

                    log.info("Processing webhook status: id={}, status={}, recipient={}", messageId, status, recipientId);
                    updateOutboxAndLog(messageId, status, recipientId);
                }
            }
        }
    }

    private void updateOutboxAndLog(String messageId, String status, String recipientId) {
        if (messageId == null || messageId.isBlank()) return;

        Optional<WhatsAppOutbox> outboxOpt = outboxRepository.findByProviderMessageId(messageId);
        if (outboxOpt.isPresent()) {
            WhatsAppOutbox outbox = outboxOpt.get();
            if ("delivered".equals(status) || "read".equals(status)) {
                outbox.setStatus(WhatsAppOutboxStatus.DELIVERED);
                outbox.setReconciliationStatus("DELIVERED");
                outbox.setUpdatedAt(Instant.now());
                outboxRepository.save(outbox);
            } else if ("failed".equals(status)) {
                outbox.setStatus(WhatsAppOutboxStatus.FAILED);
                outbox.setReconciliationStatus("FAILED_CONFIRMED");
                outbox.setUpdatedAt(Instant.now());
                outboxRepository.save(outbox);
            }
        }

        Optional<WhatsAppMessageLog> logOpt = logRepository.findByProviderMessageId(messageId);
        if (logOpt.isPresent()) {
            WhatsAppMessageLog msgLog = logOpt.get();
            if ("delivered".equals(status) || "read".equals(status)) {
                msgLog.setStatus("DELIVERED");
                msgLog.setDeliveredAt(Instant.now());
                logRepository.save(msgLog);
            } else if ("failed".equals(status)) {
                msgLog.setStatus("FAILED");
                msgLog.setFailedAt(Instant.now());
                logRepository.save(msgLog);
            }
        }
    }
}
