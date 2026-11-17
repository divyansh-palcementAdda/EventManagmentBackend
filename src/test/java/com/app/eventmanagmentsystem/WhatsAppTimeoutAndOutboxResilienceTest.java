package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseMessageResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.BotsenseApiClient;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.WhatsAppNotificationService;
import com.app.eventmanagmentsystem.service.impl.WhatsAppOutboxProcessor;
import com.app.eventmanagmentsystem.service.impl.WhatsAppSettingsService;
import com.app.eventmanagmentsystem.util.QrTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
public class WhatsAppTimeoutAndOutboxResilienceTest {

        @Autowired
        private WhatsAppOutboxRepository outboxRepository;

        @Autowired
        private WhatsAppMessageLogRepository messageLogRepository;

        @Autowired
        private WhatsAppNotificationService whatsAppNotificationService;

        @Autowired
        private WhatsAppOutboxProcessor outboxProcessor;

        @Autowired
        private WhatsAppSettingsService settingsService;

        @Autowired
        private WhatsAppNotificationSettingsRepository settingsRepository;

        @Autowired
        private EventRepository eventRepository;

        @Autowired
        private EventPassRepository passRepository;

        @Autowired
        private AttendeeRepository attendeeRepository;

        @Autowired
        private PassActivationService passActivationService;

        @Autowired
        private EmailOutboxRepository emailOutboxRepository;

        @Autowired
        private QrTokenUtil qrTokenUtil;

        @MockBean
        private BotsenseApiClient botsenseApiClient;

        private Event testEvent;
        private Attendee testAttendee;
        private EventPass testPass;
        private String rawToken;

        @BeforeEach
        void setUp() {
                outboxRepository.deleteAll();
                messageLogRepository.deleteAll();

                // Default mock for BotsenseApiClient
                when(botsenseApiClient.isConfigured()).thenReturn(true);

                // Ensure settings are enabled for tests
                WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();
                settings.setIntegrationEnabled(true);
                settings.setPassActivationNotificationEnabled(true);
                settings.setEventReminderEnabled(true);
                settings.setOtpVerificationEnabled(false);
                settings.setPassActivationTemplateName("rcf_activation_pass");
                settings.setPassActivationTemplateLanguage("en");
                settingsRepository.save(settings);

                testEvent = new Event();
                testEvent.setName("Timeout Resilience Summit");
                testEvent.setEventCode("TRS" + System.currentTimeMillis() % 10000);
                testEvent.setPassSeriesCode("TS");
                testEvent.setStatus(EventStatus.PUBLISHED);
                testEvent.setStartsAt(Instant.now().plus(Duration.ofDays(2)));
                testEvent.setEndsAt(Instant.now().plus(Duration.ofDays(3)));
                testEvent.setVenue("Main Hall");
                testEvent = eventRepository.save(testEvent);

                testAttendee = new Attendee();
                testAttendee.setEvent(testEvent);
                testAttendee.setFullName("Aarav Mehta");
                testAttendee.setEmail("aarav.mehta." + UUID.randomUUID() + "@test.local");
                testAttendee.setMobileNumber("9876543210");
                testAttendee.setRegisteredAt(Instant.now());
                testAttendee = attendeeRepository.save(testAttendee);

                rawToken = qrTokenUtil.generateRawToken();
                testPass = new EventPass();
                testPass.setEvent(testEvent);
                testPass.setPassNumber("TRS-TS-" + UUID.randomUUID().toString().substring(0, 8));
                testPass.setSequenceNumber(System.currentTimeMillis() % 100000);
                testPass.setTokenHash(qrTokenUtil.hashToken(rawToken));
                testPass.setCategory("STUDENT");
                testPass.setStatus(PassStatus.NOT_ACTIVATED);
                testPass.setGeneratedAt(Instant.now());
                testPass = passRepository.save(testPass);
        }

        @Test
        @DisplayName("Scenario 1: Botsense returns 201/success -> message marked SENT, provider ID saved, no retry")
        void testScenario1_BotsenseReturnsSuccess_MarkedSentNoRetry() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .messages(List.of(new BotsenseMessageResponse.MessageRef(
                                                                "wamid.mock.success.123", "sent", "sent")))
                                                .build());

                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> items = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                assertThat(items).hasSize(1);
                UUID outboxId = items.get(0).getId();

                outboxProcessor.processOutbox();

                WhatsAppOutbox processed = outboxRepository.findById(outboxId).orElseThrow();
                assertThat(processed.getStatus()).isEqualTo(WhatsAppOutboxStatus.SENT);
                assertThat(processed.getProviderMessageId()).isEqualTo("wamid.mock.success.123");
                assertThat(processed.getNextRetryAt()).isNull();
                assertThat(processed.getAttemptCount()).isEqualTo(1);

                // Verify outboxProcessor does NOT attempt redelivery
                outboxProcessor.processOutbox();
                verify(botsenseApiClient, times(1)).sendTemplateMessage(anyString(), anyString(), anyString(),
                                anyList());
        }

        @Test
        @DisplayName("Scenario 2: Read timeout waiting for response -> message marked UNKNOWN, NO automatic retry")
        void testScenario2_BotsenseReadTimeout_MarkedUnknownNoAutoRetry() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .object("error")
                                                .error(new BotsenseMessageResponse.ErrorDetails(
                                                                "Read timed out waiting for Botsense response",
                                                                "READ_TIMEOUT",
                                                                504,
                                                                "READ_TIMEOUT"))
                                                .build());

                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> items = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                assertThat(items).hasSize(1);
                UUID outboxId = items.get(0).getId();

                outboxProcessor.processOutbox();

                WhatsAppOutbox item = outboxRepository.findById(outboxId).orElseThrow();
                assertThat(item.getStatus()).isEqualTo(WhatsAppOutboxStatus.UNKNOWN);
                assertThat(item.getLastErrorCategory()).isEqualTo("READ_TIMEOUT");
                assertThat(item.getUnknownOutcomeAt()).isNotNull();
                assertThat(item.getNextRetryAt()).isNull(); // Crucial: must NOT schedule automatic retry!
                assertThat(item.getAttemptCount()).isEqualTo(1);

                // Run outbox processor again — UNKNOWN must NOT be automatically dispatched!
                outboxProcessor.processOutbox();
                verify(botsenseApiClient, times(1)).sendTemplateMessage(anyString(), anyString(), anyString(),
                                anyList());

                // Verify audit log exists with UNKNOWN status
                boolean logExists = messageLogRepository.existsByRecipientMobileAndNotificationTypeAndStatus(
                                BotsenseApiClient.formatPhoneNumber("9876543210"),
                                WhatsAppNotificationType.PASS_ACTIVATION,
                                "UNKNOWN");
                assertThat(logExists).isTrue();
        }

        @Test
        @DisplayName("Scenario 3: Connection failure before transmission -> marked RETRY_WAIT, safe retry scheduled")
        void testScenario3_ConnectionFailsBeforeTransmission_SafeRetry() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .object("error")
                                                .error(new BotsenseMessageResponse.ErrorDetails(
                                                                "Connection failure before transmission: Connection refused",
                                                                "CONNECT_FAILURE",
                                                                503,
                                                                "CONNECT_FAILURE"))
                                                .build());

                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> items = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                UUID outboxId = items.get(0).getId();

                outboxProcessor.processOutbox();

                WhatsAppOutbox item = outboxRepository.findById(outboxId).orElseThrow();
                assertThat(item.getStatus()).isEqualTo(WhatsAppOutboxStatus.RETRY_WAIT);
                assertThat(item.getLastErrorCategory()).isEqualTo("CONNECT_FAILURE");
                assertThat(item.getNextRetryAt()).isNotNull();
                assertThat(item.getAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Scenario 4: Botsense returns permanent 400 client error -> marked FAILED_PERMANENTLY, no retry")
        void testScenario4_BotsenseReturnsPermanent400_MarkedFailedPermanently() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .object("error")
                                                .error(new BotsenseMessageResponse.ErrorDetails(
                                                                "template.name: Template does not exist",
                                                                "invalid_request_error",
                                                                400,
                                                                "invalid_request_body"))
                                                .build());

                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> items = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                UUID outboxId = items.get(0).getId();

                outboxProcessor.processOutbox();

                WhatsAppOutbox item = outboxRepository.findById(outboxId).orElseThrow();
                assertThat(item.getStatus()).isEqualTo(WhatsAppOutboxStatus.FAILED_PERMANENTLY);
                assertThat(item.getLastErrorCategory()).isEqualTo("VALIDATION_ERROR");
                assertThat(item.getNextRetryAt()).isNull();

                // Second processor run must not retry
                outboxProcessor.processOutbox();
                verify(botsenseApiClient, times(1)).sendTemplateMessage(anyString(), anyString(), anyString(),
                                anyList());
        }

        @Test
        @DisplayName("Scenario 5: Botsense returns 429 rate limit -> bounded retry with backoff")
        void testScenario5_BotsenseReturns429_BoundedRetry() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .object("error")
                                                .error(new BotsenseMessageResponse.ErrorDetails(
                                                                "Rate limit exceeded. Try again later.",
                                                                "rate_limit_error",
                                                                429,
                                                                "RATE_LIMIT"))
                                                .build());

                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> items = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                WhatsAppOutbox item = items.get(0);
                item.setAttemptCount(4); // 4th attempt out of 5
                outboxRepository.save(item);

                outboxProcessor.processOutbox();

                WhatsAppOutbox updated = outboxRepository.findById(item.getId()).orElseThrow();
                // 5th attempt reached maxAttempts -> FAILED_PERMANENTLY
                assertThat(updated.getStatus()).isEqualTo(WhatsAppOutboxStatus.FAILED_PERMANENTLY);
                assertThat(updated.getLastErrorCategory()).isEqualTo("RATE_LIMIT");
                assertThat(updated.getNextRetryAt()).isNull();
        }

        @Test
        @DisplayName("Scenario 6: Multiple workers attempt to claim the same record -> exactly one succeeds")
        void testScenario6_MultipleWorkersAttemptToClaimRecord_OnlyOneDispatch() throws Exception {
                WhatsAppOutbox item = outboxRepository.save(WhatsAppOutbox.builder()
                                .recipientMobile("+919876543210")
                                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                                .templateName("rcf_activation_pass")
                                .status(WhatsAppOutboxStatus.PENDING)
                                .nextRetryAt(Instant.now().minusSeconds(10))
                                .build());

                int numWorkers = 5;
                ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
                CountDownLatch startLatch = new CountDownLatch(1);
                AtomicInteger successfulClaims = new AtomicInteger(0);

                List<Future<?>> futures = new ArrayList<>();
                Instant now = Instant.now();
                Instant leaseUntil = now.plus(Duration.ofMinutes(2));

                for (int i = 0; i < numWorkers; i++) {
                        futures.add(executor.submit(() -> {
                                try {
                                        startLatch.await();
                                        int claimed = outboxProcessor.claimRecordAtomically(item.getId(), leaseUntil,
                                                        now);
                                        if (claimed > 0) {
                                                successfulClaims.incrementAndGet();
                                        }
                                } catch (Exception e) {
                                        // Ignore
                                }
                        }));
                }

                startLatch.countDown();
                for (Future<?> f : futures) {
                        f.get(5, TimeUnit.SECONDS);
                }
                executor.shutdown();

                assertThat(successfulClaims.get()).isEqualTo(1);
                WhatsAppOutbox claimed = outboxRepository.findById(item.getId()).orElseThrow();
                assertThat(claimed.getStatus()).isEqualTo(WhatsAppOutboxStatus.PROCESSING);
        }

        @Test
        @DisplayName("Scenario 7: Duplicate activation event -> only one logical outbox record created")
        void testScenario7_DuplicateActivationEvent_OnlyOneLogicalOutboxRecord() {
                // First enqueue
                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> firstCheck = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                assertThat(firstCheck).hasSize(1);

                // Second duplicate enqueue for same pass
                whatsAppNotificationService.enqueuePassActivation(testPass, testAttendee);
                List<WhatsAppOutbox> secondCheck = outboxRepository.findByReferenceIdAndNotificationType(
                                testPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);

                assertThat(secondCheck).hasSize(1); // Still exactly one record!
        }

        @Test
        @DisplayName("Scenario 8: Reconciliation confirms message acceptance -> marked SENT, no resend")
        void testScenario8_ReconciliationConfirmsAcceptance_MarkedSentNoResend() {
                WhatsAppOutbox item = outboxRepository.save(WhatsAppOutbox.builder()
                                .recipientMobile("+919876543210")
                                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                                .templateName("rcf_activation_pass")
                                .status(WhatsAppOutboxStatus.UNKNOWN)
                                .unknownOutcomeAt(Instant.now().minusSeconds(60))
                                .providerMessageId("wamid.recon.test.888")
                                .build());

                // Mock provider status check confirming SENT
                when(botsenseApiClient.checkMessageStatus("wamid.recon.test.888"))
                                .thenReturn(Optional.of(Map.of("status", "SENT", "id", "wamid.recon.test.888")));

                outboxProcessor.reconcileRecord(item);

                WhatsAppOutbox reconciled = outboxRepository.findById(item.getId()).orElseThrow();
                assertThat(reconciled.getStatus()).isEqualTo(WhatsAppOutboxStatus.SENT);
                assertThat(reconciled.getReconciliationStatus()).isEqualTo("ACCEPTED");
                verify(botsenseApiClient, never()).sendTemplateMessage(anyString(), anyString(), anyString(),
                                anyList());
        }

        @Test
        @DisplayName("Scenario 9: Reconciliation confirms non-acceptance -> scheduled for safe retry")
        void testScenario9_ReconciliationConfirmsNonAcceptance_SafeRetry() {
                WhatsAppOutbox item = outboxRepository.save(WhatsAppOutbox.builder()
                                .recipientMobile("+919876543210")
                                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                                .templateName("rcf_activation_pass")
                                .status(WhatsAppOutboxStatus.UNKNOWN)
                                .unknownOutcomeAt(Instant.now().minusSeconds(60))
                                .providerMessageId("wamid.failed.recon.999")
                                .build());

                // Mock provider confirming failure
                when(botsenseApiClient.checkMessageStatus("wamid.failed.recon.999"))
                                .thenReturn(Optional.of(Map.of("status", "FAILED", "error",
                                                "Message not delivered to handset")));

                outboxProcessor.reconcileRecord(item);

                WhatsAppOutbox reconciled = outboxRepository.findById(item.getId()).orElseThrow();
                assertThat(reconciled.getStatus()).isEqualTo(WhatsAppOutboxStatus.RETRY_WAIT);
                assertThat(reconciled.getReconciliationStatus()).isEqualTo("NOT_ACCEPTED");
                assertThat(reconciled.getNextRetryAt()).isNotNull();
        }

        @Test
        @DisplayName("Scenario 10: Reconciliation cannot determine outcome -> marked MANUAL_REVIEW")
        void testScenario10_ReconciliationCannotDetermineOutcome_MarkedManualReview() {
                WhatsAppOutbox item = outboxRepository.save(WhatsAppOutbox.builder()
                                .recipientMobile("+919876543210")
                                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                                .templateName("rcf_activation_pass")
                                .status(WhatsAppOutboxStatus.UNKNOWN)
                                .unknownOutcomeAt(Instant.now().minusSeconds(60))
                                .reconciliationAttempts(2) // already attempted twice
                                .build());

                // Mock provider returning empty (inconclusive / endpoint not supported)
                when(botsenseApiClient.checkMessageByRecipient(anyString())).thenReturn(Optional.empty());

                outboxProcessor.reconcileRecord(item);

                WhatsAppOutbox reconciled = outboxRepository.findById(item.getId()).orElseThrow();
                assertThat(reconciled.getStatus()).isEqualTo(WhatsAppOutboxStatus.MANUAL_REVIEW);
                assertThat(reconciled.getReconciliationStatus()).isEqualTo("AMBIGUOUS");
        }

        @Test
        @DisplayName("Scenario 11: Email delivery remains completely independent of WhatsApp status")
        void testScenario11_EmailDeliveryRemainsCompletelyIndependent() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .object("error")
                                                .error(new BotsenseMessageResponse.ErrorDetails(
                                                                "Read timed out waiting for Botsense response",
                                                                "READ_TIMEOUT",
                                                                504,
                                                                "READ_TIMEOUT"))
                                                .build());

                String activationToken = qrTokenUtil.generateRawToken();
                EventPass unactivatedPass = new EventPass();
                unactivatedPass.setEvent(testEvent);
                unactivatedPass.setPassNumber("TRS-TS-" + UUID.randomUUID().toString().substring(0, 8));
                unactivatedPass.setSequenceNumber(System.currentTimeMillis() % 100000);
                unactivatedPass.setTokenHash(qrTokenUtil.hashToken(activationToken));
                unactivatedPass.setCategory("GENERAL");
                unactivatedPass.setStatus(PassStatus.NOT_ACTIVATED);
                unactivatedPass.setGeneratedAt(Instant.now());
                unactivatedPass = passRepository.save(unactivatedPass);

                ActivatePassRequest req = new ActivatePassRequest();
                req.setRawToken(activationToken);
                req.setEventId(testEvent.getId());
                req.setAttendeeFullName("Distinct Attendee 1");
                req.setAttendeeEmail("distinct1." + UUID.randomUUID() + "@test.local");
                req.setAttendeeMobileNumber("9876543211");
                req.setAttendeeCollege("Renaissance University");
                req.setAttendeeCourse("Computer Science");

                passActivationService.activatePass(req);

                // WhatsApp outbox item should be enqueued and marked UNKNOWN upon processing
                List<WhatsAppOutbox> waItems = outboxRepository.findByReferenceIdAndNotificationType(
                                unactivatedPass.getId().toString(), WhatsAppNotificationType.PASS_ACTIVATION);
                assertThat(waItems).isNotEmpty();

                outboxProcessor.processOutbox();
                WhatsAppOutbox waProcessed = outboxRepository.findById(waItems.get(0).getId()).orElseThrow();
                assertThat(waProcessed.getStatus()).isEqualTo(WhatsAppOutboxStatus.UNKNOWN);

                // Email outbox records exist independently
                List<EmailOutbox> emailItems = emailOutboxRepository.findAll();
                assertThat(emailItems).isNotEmpty();
        }

        @Test
        @DisplayName("Scenario 12: Pass activation and QR generation remain unaffected by WhatsApp timeout")
        void testScenario12_PassActivationAndQrGenerationRemainUnaffected() {
                when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), anyList()))
                                .thenReturn(BotsenseMessageResponse.builder()
                                                .object("error")
                                                .error(new BotsenseMessageResponse.ErrorDetails(
                                                                "Read timed out waiting for Botsense response",
                                                                "READ_TIMEOUT",
                                                                504,
                                                                "READ_TIMEOUT"))
                                                .build());

                String activationToken = qrTokenUtil.generateRawToken();
                EventPass unactivatedPass = new EventPass();
                unactivatedPass.setEvent(testEvent);
                unactivatedPass.setPassNumber("TRS-TS-" + UUID.randomUUID().toString().substring(0, 8));
                unactivatedPass.setSequenceNumber(System.currentTimeMillis() % 100000);
                unactivatedPass.setTokenHash(qrTokenUtil.hashToken(activationToken));
                unactivatedPass.setCategory("GENERAL");
                unactivatedPass.setStatus(PassStatus.NOT_ACTIVATED);
                unactivatedPass.setGeneratedAt(Instant.now());
                unactivatedPass = passRepository.save(unactivatedPass);

                ActivatePassRequest req = new ActivatePassRequest();
                req.setRawToken(activationToken);
                req.setEventId(testEvent.getId());
                req.setAttendeeFullName("Distinct Attendee 2");
                req.setAttendeeEmail("distinct2." + UUID.randomUUID() + "@test.local");
                req.setAttendeeMobileNumber("9876543212");
                req.setAttendeeCollege("Renaissance University");
                req.setAttendeeCourse("Computer Science");

                passActivationService.activatePass(req);

                EventPass activatedPass = passRepository.findById(unactivatedPass.getId()).orElseThrow();
                assertThat(activatedPass.getStatus()).isEqualTo(PassStatus.ACTIVATED);
                assertThat(activatedPass.getActivatedAt()).isNotNull();
                assertThat(activatedPass.getTokenHash()).isNotBlank();
        }
}
