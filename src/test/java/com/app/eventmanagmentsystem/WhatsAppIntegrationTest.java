package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.whatsapp.*;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.BotsenseApiClient;
import com.app.eventmanagmentsystem.service.impl.EventReminderScheduler;
import com.app.eventmanagmentsystem.service.impl.WhatsAppNotificationService;
import com.app.eventmanagmentsystem.service.impl.WhatsAppOtpService;
import com.app.eventmanagmentsystem.service.impl.WhatsAppOutboxProcessor;
import com.app.eventmanagmentsystem.service.impl.WhatsAppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
public class WhatsAppIntegrationTest {

    @Autowired
    private WhatsAppSettingsService settingsService;

    @Autowired
    private WhatsAppOtpService otpService;

    @Autowired
    private WhatsAppNotificationService notificationService;

    @Autowired
    private WhatsAppOutboxProcessor outboxProcessor;

    @Autowired
    private EventReminderScheduler reminderScheduler;

    @Autowired
    private WhatsAppNotificationSettingsRepository settingsRepository;

    @Autowired
    private WhatsAppOtpVerificationRepository otpVerificationRepository;

    @Autowired
    private WhatsAppOutboxRepository outboxRepository;

    @Autowired
    private WhatsAppMessageLogRepository messageLogRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPassRepository passRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @MockBean
    private BotsenseApiClient botsenseApiClient;

    private Event testEvent;
    private EventPass testPass;
    private Attendee testAttendee;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        messageLogRepository.deleteAll();
        otpVerificationRepository.deleteAll();

        // Default mock for BotsenseApiClient
        when(botsenseApiClient.isConfigured()).thenReturn(true);
        when(botsenseApiClient.uploadMedia(any(), anyString(), anyString())).thenReturn("mock.media.id.789");
        when(botsenseApiClient.sendOtpTemplate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(BotsenseMessageResponse.builder()
                        .messages(List.of(new BotsenseMessageResponse.MessageRef("wamid.mock.otp.123", "sent", "sent")))
                        .build());
        when(botsenseApiClient.sendTemplateMessage(anyString(), anyString(), anyString(), any()))
                .thenReturn(BotsenseMessageResponse.builder()
                        .messages(List.of(new BotsenseMessageResponse.MessageRef("wamid.mock.tpl.456", "sent", "sent")))
                        .build());

        testEvent = new Event();
        testEvent.setName("WhatsApp Test Summit");
        testEvent.setEventCode("WATS" + System.currentTimeMillis() % 10000);
        testEvent.setPassSeriesCode("WS");
        testEvent.setStatus(EventStatus.PUBLISHED);
        testEvent.setStartsAt(Instant.now().plus(Duration.ofHours(12)));
        testEvent.setEndsAt(Instant.now().plus(Duration.ofHours(24)));
        testEvent.setVenue("Test Hall");
        testEvent = eventRepository.save(testEvent);

        testAttendee = new Attendee();
        testAttendee.setEvent(testEvent);
        testAttendee.setFullName("Rohan Sharma");
        testAttendee.setEmail("rohan" + System.currentTimeMillis() + "@test.com");
        testAttendee.setMobileNumber("9876543210");
        testAttendee.setRegisteredAt(Instant.now());
        testAttendee = attendeeRepository.save(testAttendee);

        testPass = new EventPass();
        testPass.setEvent(testEvent);
        testPass.setPassNumber("WATS-WS-" + UUID.randomUUID().toString().substring(0, 8));
        testPass.setSequenceNumber(System.currentTimeMillis() % 100000);
        testPass.setStatus(PassStatus.NOT_ACTIVATED);
        testPass.setGeneratedAt(Instant.now());
        testPass = passRepository.save(testPass);
    }

    private void configureToggles(boolean master, boolean otp, boolean activation, boolean reminder) {
        WhatsAppNotificationSettings s = settingsService.getOrCreateSettings();
        s.setIntegrationEnabled(master);
        s.setOtpVerificationEnabled(otp);
        s.setPassActivationNotificationEnabled(activation);
        s.setEventReminderEnabled(reminder);
        settingsRepository.save(s);
    }

    @Test
    @DisplayName("Toggle Matrix 1: Master OFF suppresses all outbound activity")
    void testMasterOffSuppressesAll() {
        configureToggles(false, true, true, true);

        // 1. OTP check
        assertThat(otpService.isOtpRequired(testEvent.getId())).isFalse();
        SendOtpRequest otpReq = new SendOtpRequest();
        otpReq.setMobileNumber("9876543210");
        otpReq.setEventId(testEvent.getId());
        OtpVerificationResponse otpRes = otpService.sendOtp(otpReq);
        assertThat(otpRes.isVerified()).isTrue(); // Bypassed
        verify(botsenseApiClient, never()).sendOtpTemplate(anyString(), anyString(), anyString(), anyString());

        // 2. Pass activation enqueue
        notificationService.enqueuePassActivation(testPass, testAttendee);
        assertThat(outboxRepository.count()).isZero();

        // 3. Reminders
        reminderScheduler.scanAndEnqueueReminders();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("Toggle Matrix 2: Master ON, OTP ON, Pass Activation OFF, Reminder OFF")
    void testOtpOnlyEnabled() {
        configureToggles(true, true, false, false);

        // OTP is required
        assertThat(otpService.isOtpRequired(testEvent.getId())).isTrue();
        SendOtpRequest otpReq = new SendOtpRequest();
        otpReq.setMobileNumber("9876543210");
        otpReq.setEventId(testEvent.getId());
        OtpVerificationResponse otpRes = otpService.sendOtp(otpReq);
        assertThat(otpRes.isVerified()).isFalse();
        verify(botsenseApiClient, times(1)).sendOtpTemplate(anyString(), anyString(), anyString(), anyString());

        // Pass activation is bypassed
        notificationService.enqueuePassActivation(testPass, testAttendee);
        assertThat(outboxRepository.count()).isZero();

        // Reminder is bypassed
        reminderScheduler.scanAndEnqueueReminders();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("Toggle Matrix 3: Master ON, OTP OFF, Pass Activation ON, Reminder OFF")
    void testPassActivationOnlyEnabled() {
        configureToggles(true, false, true, false);

        // OTP is bypassed
        assertThat(otpService.isOtpRequired(testEvent.getId())).isFalse();

        // Pass activation is enqueued
        notificationService.enqueuePassActivation(testPass, testAttendee);
        assertThat(outboxRepository.count()).isEqualTo(1);
        WhatsAppOutbox item = outboxRepository.findAll().get(0);
        assertThat(item.getNotificationType()).isEqualTo(WhatsAppNotificationType.PASS_ACTIVATION);
        assertThat(item.getStatus()).isEqualTo(WhatsAppOutboxStatus.PENDING);

        // Process outbox
        outboxProcessor.processOutbox();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<BotsenseMessageRequest.Component>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(botsenseApiClient, times(1)).sendTemplateMessage(anyString(), anyString(), anyString(), captor.capture());

        List<BotsenseMessageRequest.Component> sentComponents = captor.getValue();
        assertThat(sentComponents).isNotNull();
        assertThat(sentComponents).hasSize(1);
        assertThat(sentComponents.get(0).getType()).isEqualTo("header");
        assertThat(sentComponents.get(0).getParameters().get(0).getType()).isEqualTo("image");
        assertThat(sentComponents.get(0).getParameters().get(0).getImage().getLink())
                .isEqualTo("https://event.areyoureporting.com/api/v1/passes/" + testPass.getId() + "/whatsapp-pass-image");
        assertThat(sentComponents.get(0).getParameters().get(0).getImage().getId()).isNull();

        // Verify NO body components exist
        assertThat(sentComponents.stream().noneMatch(c -> "body".equalsIgnoreCase(c.getType()))).isTrue();

        WhatsAppOutbox processed = outboxRepository.findById(item.getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(WhatsAppOutboxStatus.SENT);
    }

    @Test
    @DisplayName("Pass Activation with Public HTTPS Link: uses link in header IMAGE param with 0 body params")
    void testPassActivationWithPublicLinkImage() {
        configureToggles(true, false, true, false);

        // Enqueue with public image link in payload
        WhatsAppOutbox item = WhatsAppOutbox.builder()
                .recipientMobile("+919876543210")
                .recipientName("Link Attendee")
                .notificationType(WhatsAppNotificationType.PASS_ACTIVATION)
                .referenceId(testPass.getId().toString())
                .templateName("rcf_activation_pass")
                .payload("{\"passNumber\":\"" + testPass.getPassNumber() + "\",\"imageLink\":\"https://cdn.eventpass.io/passes/test.png\"}")
                .status(WhatsAppOutboxStatus.PENDING)
                .build();
        outboxRepository.save(item);

        outboxProcessor.processOutbox();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<BotsenseMessageRequest.Component>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(botsenseApiClient, atLeastOnce()).sendTemplateMessage(anyString(), anyString(), anyString(), captor.capture());

        List<BotsenseMessageRequest.Component> sentComponents = captor.getValue();
        assertThat(sentComponents).isNotNull();
        assertThat(sentComponents).hasSize(1);
        assertThat(sentComponents.get(0).getType()).isEqualTo("header");
        assertThat(sentComponents.get(0).getParameters().get(0).getType()).isEqualTo("image");
        assertThat(sentComponents.get(0).getParameters().get(0).getImage().getLink()).isEqualTo("https://cdn.eventpass.io/passes/test.png");
        assertThat(sentComponents.get(0).getParameters().get(0).getImage().getId()).isNull();

        // 0 body components
        assertThat(sentComponents.stream().noneMatch(c -> "body".equalsIgnoreCase(c.getType()))).isTrue();
    }

    @Test
    @DisplayName("Toggle Matrix 4: Master ON, OTP OFF, Pass Activation OFF, Reminder ON")
    void testReminderOnlyEnabled() {
        configureToggles(true, false, false, true);

        // Mark pass as activated so it is eligible for reminder
        testPass.setStatus(PassStatus.ACTIVATED);
        testPass.setAttendee(testAttendee);
        passRepository.save(testPass);

        reminderScheduler.scanAndEnqueueReminders();
        assertThat(outboxRepository.count()).isEqualTo(1);
        WhatsAppOutbox item = outboxRepository.findAll().get(0);
        assertThat(item.getNotificationType()).isEqualTo(WhatsAppNotificationType.EVENT_REMINDER);

        // Process reminder
        outboxProcessor.processOutbox();
        verify(botsenseApiClient, times(1)).sendTemplateMessage(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Runtime Safety: Toggle turned OFF after job is enqueued causes SUPPRESSION without sending")
    void testRuntimeToggleSuppression() {
        configureToggles(true, false, true, false);

        // Enqueue pass activation
        notificationService.enqueuePassActivation(testPass, testAttendee);
        assertThat(outboxRepository.count()).isEqualTo(1);

        // Administrator turns pass activation toggle OFF before processor runs!
        configureToggles(true, false, false, false);

        outboxProcessor.processOutbox();

        // Outbox item must be SUPPRESSED, and Botsense must NOT be called
        verify(botsenseApiClient, never()).sendTemplateMessage(anyString(), anyString(), anyString(), any());
        WhatsAppOutbox suppressed = outboxRepository.findAll().get(0);
        assertThat(suppressed.getStatus()).isEqualTo(WhatsAppOutboxStatus.SUPPRESSED);
    }

    @Test
    @DisplayName("OTP Lifecycle: Send, verify valid code, and enforce token validation")
    void testOtpVerificationLifecycle() {
        configureToggles(true, true, false, false);

        SendOtpRequest sendReq = new SendOtpRequest();
        sendReq.setMobileNumber("+919876543210");
        sendReq.setEventId(testEvent.getId());
        otpService.sendOtp(sendReq);

        WhatsAppOtpVerification verification = otpVerificationRepository.findAll().get(0);
        assertThat(verification.isVerified()).isFalse();

        // 1. Invalid code attempt
        VerifyOtpRequest badReq = new VerifyOtpRequest();
        badReq.setMobileNumber("+919876543210");
        badReq.setOtp("000000");
        badReq.setEventId(testEvent.getId());

        assertThatThrownBy(() -> otpService.verifyOtp(badReq))
                .isInstanceOf(RuntimeException.class);

        // 2. Dynamic toggle disabled while verification pending: attendee is NOT blocked
        configureToggles(true, false, false, false);
        boolean allowedWithoutToken = otpService.validateVerificationToken("9876543210", null, testEvent.getId());
        assertThat(allowedWithoutToken).isTrue();
    }
}
