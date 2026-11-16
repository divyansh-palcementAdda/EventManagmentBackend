package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseTemplateItem;
import com.app.eventmanagmentsystem.payload.whatsapp.UpdateWhatsAppSettingsRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.WhatsAppOutboxResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.WhatsAppSettingsResponse;
import com.app.eventmanagmentsystem.repository.WhatsAppOutboxRepository;
import com.app.eventmanagmentsystem.model.WhatsAppOutbox;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.model.enums.WhatsAppOutboxStatus;
import com.app.eventmanagmentsystem.service.impl.WhatsAppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "WhatsApp Settings", description = "Endpoints for managing Botsense WhatsApp integration settings, independent feature toggles, and templates")
@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
public class WhatsAppSettingsController {

    private final WhatsAppSettingsService settingsService;
    private final com.app.eventmanagmentsystem.service.impl.WhatsAppOutboxProcessor outboxProcessor;
    private final WhatsAppOutboxRepository outboxRepository;

    @Operation(summary = "Get WhatsApp integration settings", description = "Retrieves master and independent feature toggles (OTP, Pass Activation, Reminders) and template mappings.")
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getSettings()));
    }

    @Operation(summary = "Update WhatsApp integration settings", description = "Updates master toggle and independent feature toggles. Requires WHATSAPP_CONFIG_MANAGE.")
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateWhatsAppSettingsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "admin";
        WhatsAppSettingsResponse response = settingsService.updateSettings(request, username);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings updated successfully", response));
    }

    @Operation(summary = "Test Botsense WhatsApp API connection", description = "Tests connection to Botsense API and verifies connected phone numbers.")
    @PostMapping("/test-connection")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> testConnection() {
        return ResponseEntity.ok(ApiResponse.success("Connection test completed", settingsService.testConnection()));
    }

    @Operation(summary = "List approved WhatsApp templates", description = "Retrieves all approved templates from Botsense for admin mapping.")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('WHATSAPP_TEMPLATE_VIEW')")
    public ResponseEntity<ApiResponse<List<BotsenseTemplateItem>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAvailableTemplates()));
    }

    @Operation(summary = "Retry failed WhatsApp outbox messages", description = "Safely re-queues failed messages for redelivery after template payload correction, with deduplication checks.")
    @PostMapping("/outbox/retry-failed")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> retryFailedMessages() {
        int count = outboxProcessor.retryFailedMessages();
        return ResponseEntity.ok(ApiResponse.success("Re-queued " + count + " failed message(s) for delivery",
                java.util.Map.of("requeuedCount", count)));
    }

    @Operation(summary = "List WhatsApp outbox items", description = "Retrieves paginated outbox items with state, attempts, timeout status, and reconciliation details.")
    @GetMapping("/outbox")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    public ResponseEntity<ApiResponse<Page<WhatsAppOutboxResponse>>> listOutbox(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WhatsAppNotificationType type,
            @RequestParam(required = false) WhatsAppOutboxStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(direction, sortBy));

        Specification<WhatsAppOutbox> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("recipientMobile"), pattern),
                        cb.like(root.get("recipientName"), pattern),
                        cb.like(root.get("templateName"), pattern),
                        cb.like(root.get("referenceId"), pattern),
                        cb.like(root.get("providerMessageId"), pattern)
                ));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("notificationType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WhatsAppOutbox> result = outboxRepository.findAll(spec, pageable);
        Page<WhatsAppOutboxResponse> responses = result.map(this::toOutboxResponse);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "Manually resend an ambiguous or failed WhatsApp outbox record",
               description = "Requires explicit admin confirmation. Operator must acknowledge the message may already have been delivered.")
    @PostMapping("/outbox/{outboxId}/manual-resend")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<String>> manualResend(
            @Parameter(description = "Outbox UUID") @PathVariable UUID outboxId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String operator = userDetails != null ? userDetails.getUsername() : "admin";
        outboxProcessor.manualResend(outboxId, operator);
        return ResponseEntity.ok(ApiResponse.success("Message re-queued for delivery by " + operator, null));
    }

    @Operation(summary = "Trigger reconciliation for an UNKNOWN outbox record",
               description = "Checks message audit logs and provider APIs to reconcile delivery status.")
    @PostMapping("/outbox/{outboxId}/reconcile")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<WhatsAppOutboxResponse>> reconcile(
            @Parameter(description = "Outbox UUID") @PathVariable UUID outboxId) {

        WhatsAppOutbox item = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox record not found: " + outboxId));
        outboxProcessor.reconcileRecord(item);
        return ResponseEntity.ok(ApiResponse.success("Reconciliation completed", toOutboxResponse(item)));
    }

    private WhatsAppOutboxResponse toOutboxResponse(WhatsAppOutbox o) {
        return WhatsAppOutboxResponse.builder()
                .id(o.getId())
                .recipientMobile(o.getRecipientMobile())
                .maskedMobile(WhatsAppOutboxResponse.maskPhoneNumber(o.getRecipientMobile()))
                .recipientName(o.getRecipientName())
                .notificationType(o.getNotificationType())
                .referenceId(o.getReferenceId())
                .templateName(o.getTemplateName())
                .status(o.getStatus())
                .attemptCount(o.getAttemptCount())
                .maxAttempts(o.getMaxAttempts())
                .lastError(o.getLastError())
                .lastErrorCategory(o.getLastErrorCategory())
                .nextRetryAt(o.getNextRetryAt())
                .providerMessageId(o.getProviderMessageId())
                .lastDispatchAt(o.getLastDispatchAt())
                .unknownOutcomeAt(o.getUnknownOutcomeAt())
                .reconciliationStatus(o.getReconciliationStatus())
                .reconciliationAttempts(o.getReconciliationAttempts())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}
