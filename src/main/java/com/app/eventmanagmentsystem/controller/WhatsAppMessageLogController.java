package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.WhatsAppMessageLog;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.WhatsAppMessageLogResponse;
import com.app.eventmanagmentsystem.repository.WhatsAppMessageLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "WhatsApp Message Logs", description = "Endpoints for viewing and filtering outbound WhatsApp message logs and delivery status")
@RestController
@RequestMapping("/api/v1/whatsapp/logs")
@RequiredArgsConstructor
public class WhatsAppMessageLogController {

    private final WhatsAppMessageLogRepository logRepository;

    @Operation(summary = "List and filter WhatsApp message logs", description = "Retrieves paginated message logs with filtering by mobile, type, status, and event.")
    @GetMapping
    @PreAuthorize("hasAuthority('WHATSAPP_MESSAGE_VIEW')")
    public ResponseEntity<ApiResponse<Page<WhatsAppMessageLogResponse>>> listLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) WhatsAppNotificationType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(direction, sortBy));

        Specification<WhatsAppMessageLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("recipientMobile"), pattern),
                        cb.like(root.get("providerMessageId"), pattern),
                        cb.like(root.get("templateName"), pattern)
                ));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("notificationType"), type));
            }
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WhatsAppMessageLog> result = logRepository.findAll(spec, pageable);
        Page<WhatsAppMessageLogResponse> responses = result.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    private WhatsAppMessageLogResponse toResponse(WhatsAppMessageLog l) {
        return WhatsAppMessageLogResponse.builder()
                .id(l.getId())
                .recipientMobile(l.getRecipientMobile())
                .maskedMobile(WhatsAppMessageLogResponse.maskPhoneNumber(l.getRecipientMobile()))
                .notificationType(l.getNotificationType())
                .eventId(l.getEventId())
                .passId(l.getPassId())
                .templateName(l.getTemplateName())
                .providerMessageId(l.getProviderMessageId())
                .status(l.getStatus())
                .errorCode(l.getErrorCode())
                .errorMessage(l.getErrorMessage())
                .retryCount(l.getRetryCount())
                .sentAt(l.getSentAt())
                .deliveredAt(l.getDeliveredAt())
                .failedAt(l.getFailedAt())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
