package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.config.BotsenseProperties;
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
import com.app.eventmanagmentsystem.service.impl.WhatsAppOutboxProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class BotsenseSerializationTest {

    @Autowired
    private BotsenseApiClient botsenseApiClient;

    @Autowired
    private WhatsAppOutboxProcessor outboxProcessor;

    @Autowired
    private WhatsAppOutboxRepository outboxRepository;

    @Autowired
    private WhatsAppMessageLogRepository messageLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Verify Botsense template serialization: 'language' must be a plain String, never an Object")
    void testTemplateLanguageSerializedAsPlainString() throws Exception {
        BotsenseMessageRequest.Component body = BotsenseMessageRequest.Component.builder()
                .type("body")
                .parameters(List.of(
                        BotsenseMessageRequest.Parameter.builder().type("text").text("Rohan Sharma").build(),
                        BotsenseMessageRequest.Parameter.builder().type("text").text("RCF-0001").build(),
                        BotsenseMessageRequest.Parameter.builder().type("text").text("Annual Summit").build()
                ))
                .build();

        BotsenseMessageRequest request = BotsenseMessageRequest.builder()
                .to("+919039035431")
                .fromNumberId("f401f874-6837-4091-b6a9-76d5c8031b21")
                .type("template")
                .template(BotsenseMessageRequest.TemplateContent.builder()
                        .name("rcf_activation_pass")
                        .language("en")
                        .components(List.of(body))
                        .build())
                .build();

        ObjectMapper mapper = botsenseApiClient.getObjectMapper();
        String json = mapper.writeValueAsString(request);

        // 1. Direct string checks
        assertThat(json).contains("\"language\":\"en\"");
        assertThat(json).doesNotContain("\"language\":{");
        assertThat(json).doesNotContain("\"code\":\"en\"");

        // 2. Strict AST JsonNode inspection
        JsonNode root = mapper.readTree(json);
        JsonNode templateNode = root.get("template");
        assertThat(templateNode).isNotNull();
        assertThat(templateNode.get("name").asText()).isEqualTo("rcf_activation_pass");

        JsonNode languageNode = templateNode.get("language");
        assertThat(languageNode).isNotNull();
        assertThat(languageNode.isTextual()).isTrue();
        assertThat(languageNode.isObject()).isFalse();
        assertThat(languageNode.asText()).isEqualTo("en");
    }

    @Test
    @DisplayName("Verify Botsense 400 error deserialization handles string error code without exception")
    void testBotsenseErrorDeserialization() throws Exception {
        String errorJson = "{\n" +
                "  \"error\": {\n" +
                "    \"type\": \"invalid_request_error\",\n" +
                "    \"code\": \"invalid_request_body\",\n" +
                "    \"message\": \"template.language: Invalid input: expected string, received object\",\n" +
                "    \"param\": \"template.language\"\n" +
                "  }\n" +
                "}";

        ObjectMapper mapper = botsenseApiClient.getObjectMapper();
        BotsenseMessageResponse response = mapper.readValue(errorJson, BotsenseMessageResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getType()).isEqualTo("invalid_request_error");
        assertThat(response.getError().getCode()).isEqualTo("invalid_request_body");
        assertThat(response.getError().getMessage()).contains("template.language: Invalid input: expected string, received object");
        assertThat(response.getError().getParam()).isEqualTo("template.language");
    }

    @Test
    @DisplayName("Verify error classification: HTTP 400 / payload validation errors are classified as PERMANENT (no retries)")
    void testErrorClassification() {
        // 1. Permanent error: 400 Bad Request
        BotsenseMessageResponse badRequest = BotsenseMessageResponse.builder()
                .object("error")
                .error(BotsenseMessageResponse.ErrorDetails.builder()
                        .httpStatus(400)
                        .type("invalid_request_error")
                        .code("invalid_request_body")
                        .message("template.language: Invalid input: expected string, received object")
                        .param("template.language")
                        .build())
                .build();
        assertThat(WhatsAppOutboxProcessor.isPermanentFailure(badRequest, null)).isTrue();

        // 2. Transient error: 429 Rate Limit
        BotsenseMessageResponse rateLimit = BotsenseMessageResponse.builder()
                .object("error")
                .error(BotsenseMessageResponse.ErrorDetails.builder()
                        .httpStatus(429)
                        .type("rate_limit_error")
                        .code("too_many_requests")
                        .message("Rate limit exceeded")
                        .build())
                .build();
        assertThat(WhatsAppOutboxProcessor.isPermanentFailure(rateLimit, null)).isFalse();

        // 3. Transient error: 504 Gateway Timeout
        BotsenseMessageResponse timeout = BotsenseMessageResponse.builder()
                .object("error")
                .error(BotsenseMessageResponse.ErrorDetails.builder()
                        .httpStatus(504)
                        .type("TIMEOUT_ERROR")
                        .code("NETWORK_TIMEOUT")
                        .message("Connection timed out")
                        .build())
                .build();
        assertThat(WhatsAppOutboxProcessor.isPermanentFailure(timeout, null)).isFalse();
    }

    @Test
    @DisplayName("Verify safe retry of failed outbox messages with duplicate prevention")
    @Transactional
    void testRetryFailedMessagesWithDeduplication() {
        outboxRepository.deleteAll();
        messageLogRepository.deleteAll();

        String refPassId = UUID.randomUUID().toString();
        String mobile = "+919999988888";

        // Item 1: A failed item that was NEVER sent -> should be re-queued
        WhatsAppOutbox item1 = WhatsAppOutbox.builder()
                .recipientMobile(mobile)
                .recipientName("Test Attendee 1")
                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                .referenceId(refPassId)
                .templateName("rcf_activation_pass")
                .status(WhatsAppOutboxStatus.FAILED)
                .attemptCount(5)
                .lastError("template.language: Invalid input")
                .build();
        outboxRepository.save(item1);

        // Item 2: A failed item where a SENT message log ALREADY exists -> must NOT be duplicated!
        String alreadySentPassId = UUID.randomUUID().toString();
        WhatsAppMessageLog sentLog = WhatsAppMessageLog.builder()
                .recipientMobile("+919777766666")
                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                .passId(UUID.fromString(alreadySentPassId))
                .templateName("rcf_activation_pass")
                .providerMessageId("wamid.accepted.already.123")
                .status("SENT")
                .sentAt(Instant.now())
                .build();
        messageLogRepository.save(sentLog);

        WhatsAppOutbox item2 = WhatsAppOutbox.builder()
                .recipientMobile("+919777766666")
                .recipientName("Test Attendee 2")
                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                .referenceId(alreadySentPassId)
                .templateName("rcf_activation_pass")
                .status(WhatsAppOutboxStatus.FAILED)
                .attemptCount(5)
                .lastError("Timeout while waiting for response")
                .build();
        outboxRepository.save(item2);

        // Run retryFailedMessages
        int requeued = outboxProcessor.retryFailedMessages();

        // Exactly 1 item should be re-queued (Item 1)
        assertThat(requeued).isEqualTo(1);

        // Item 1 must now be PENDING with attemptCount 0
        WhatsAppOutbox refreshed1 = outboxRepository.findById(item1.getId()).orElseThrow();
        assertThat(refreshed1.getStatus()).isEqualTo(WhatsAppOutboxStatus.PENDING);
        assertThat(refreshed1.getAttemptCount()).isZero();

        // Item 2 must be marked SENT to prevent duplicate WhatsApp message
        WhatsAppOutbox refreshed2 = outboxRepository.findById(item2.getId()).orElseThrow();
        assertThat(refreshed2.getStatus()).isEqualTo(WhatsAppOutboxStatus.SENT);
    }
}
