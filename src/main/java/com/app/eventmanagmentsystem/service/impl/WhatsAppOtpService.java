package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.WhatsAppMessageLog;
import com.app.eventmanagmentsystem.model.WhatsAppNotificationSettings;
import com.app.eventmanagmentsystem.model.WhatsAppOtpVerification;
import com.app.eventmanagmentsystem.model.enums.WhatsAppNotificationType;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseMessageResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.OtpVerificationResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.SendOtpRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.VerifyOtpRequest;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.WhatsAppMessageLogRepository;
import com.app.eventmanagmentsystem.repository.WhatsAppOtpVerificationRepository;
import com.app.eventmanagmentsystem.service.BotsenseApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppOtpService {

    private final WhatsAppSettingsService settingsService;
    private final WhatsAppOtpVerificationRepository otpVerificationRepository;
    private final WhatsAppMessageLogRepository messageLogRepository;
    private final EventRepository eventRepository;
    private final BotsenseApiClient botsenseApiClient;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_HOURLY_RESENDS = 5;

    public boolean isOtpRequired(UUID eventId) {
        WhatsAppNotificationSettings s = settingsService.getOrCreateSettings();
        return s.isIntegrationEnabled() && s.isOtpVerificationEnabled();
    }

    @Transactional
    public OtpVerificationResponse sendOtp(SendOtpRequest request) {
        if (!isOtpRequired(request.getEventId())) {
            return OtpVerificationResponse.builder()
                    .verified(true)
                    .message("WhatsApp OTP verification is currently disabled by administrator.")
                    .build();
        }

        String mobile = BotsenseApiClient.formatPhoneNumber(request.getMobileNumber());

        // Hourly rate limiting
        long recentSends = otpVerificationRepository.countByMobileNumberAndCreatedAtAfter(
                mobile, Instant.now().minus(Duration.ofHours(1)));
        if (recentSends >= MAX_HOURLY_RESENDS) {
            throw new InvalidRequestException("Too many OTP requests for this phone number. Please wait before retrying.");
        }

        // Generate 6-digit cryptographic OTP
        int codeInt = 100000 + SECURE_RANDOM.nextInt(900000);
        String otpCode = String.valueOf(codeInt);
        String otpHash = hashOtp(otpCode);

        Instant expiresAt = Instant.now().plus(OTP_EXPIRY);

        Event event = null;
        if (request.getEventId() != null) {
            event = eventRepository.findById(request.getEventId()).orElse(null);
        }

        WhatsAppOtpVerification record = WhatsAppOtpVerification.builder()
                .mobileNumber(mobile)
                .event(event)
                .otpHash(otpHash)
                .expiresAt(expiresAt)
                .attemptCount(0)
                .verified(false)
                .build();
        otpVerificationRepository.save(record);

        // Send through Botsense using configured template (e.g. 'rcf')
        WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();
        BotsenseMessageResponse response = botsenseApiClient.sendOtpTemplate(
                mobile, otpCode, settings.getOtpTemplateName(), settings.getOtpTemplateLanguage());

        String providerMsgId = response != null ? response.resolveMessageId() : null;
        String status = (response != null && response.getError() == null) ? "SENT" : "FAILED";
        String errorCode = (response != null && response.getError() != null)
                ? String.valueOf(response.getError().getCode()) : null;
        String errorMsg = (response != null && response.getError() != null)
                ? response.getError().getMessage() : null;

        // Message log
        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                .recipientMobile(mobile)
                .notificationType(WhatsAppNotificationType.OTP)
                .eventId(event != null ? event.getId() : null)
                .templateName(settings.getOtpTemplateName())
                .providerMessageId(providerMsgId)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMsg)
                .sentAt(Instant.now())
                .build();
        messageLogRepository.save(logEntry);

        log.info("Dispatched WhatsApp OTP to {} [status={}, providerId={}]",
                maskMobile(mobile), status, providerMsgId);

        return OtpVerificationResponse.builder()
                .verified(false)
                .message("Verification code sent to WhatsApp " + maskMobile(mobile))
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public OtpVerificationResponse verifyOtp(VerifyOtpRequest request) {
        if (!isOtpRequired(request.getEventId())) {
            return OtpVerificationResponse.builder()
                    .verified(true)
                    .verificationToken("BYPASS_TOKEN_" + UUID.randomUUID())
                    .message("OTP verification is disabled.")
                    .build();
        }

        String mobile = BotsenseApiClient.formatPhoneNumber(request.getMobileNumber());
        WhatsAppOtpVerification verification = otpVerificationRepository.findActivePendingVerification(mobile, Instant.now())
                .orElseThrow(() -> new InvalidRequestException("No active OTP request found for this phone number, or code has expired."));

        verification.setAttemptCount(verification.getAttemptCount() + 1);

        if (verification.getAttemptCount() > MAX_ATTEMPTS) {
            verification.setExpiresAt(Instant.now()); // Invalidate
            otpVerificationRepository.save(verification);
            throw new InvalidRequestException("Maximum verification attempts exceeded. Please request a new code.");
        }

        String enteredHash = hashOtp(request.getOtp().trim());
        if (!verification.getOtpHash().equals(enteredHash)) {
            otpVerificationRepository.save(verification);
            int remaining = MAX_ATTEMPTS - verification.getAttemptCount();
            throw new InvalidRequestException("Invalid verification code. Attempts remaining: " + Math.max(0, remaining));
        }

        // Successfully verified
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        verification.setVerified(true);
        verification.setVerifiedAt(Instant.now());
        verification.setVerificationToken(token);
        otpVerificationRepository.save(verification);

        log.info("Phone number {} successfully verified via WhatsApp OTP", maskMobile(mobile));

        return OtpVerificationResponse.builder()
                .verified(true)
                .verificationToken(token)
                .message("Phone number verified successfully.")
                .expiresAt(verification.getExpiresAt())
                .build();
    }

    @Transactional(readOnly = true)
    public boolean validateVerificationToken(String mobileNumber, String verificationToken, UUID eventId) {
        // If OTP feature is disabled, bypass check entirely!
        if (!isOtpRequired(eventId)) {
            return true;
        }

        if (verificationToken == null || verificationToken.isBlank()) {
            return false;
        }

        String formatted = BotsenseApiClient.formatPhoneNumber(mobileNumber);
        return otpVerificationRepository.findByVerificationTokenAndVerifiedTrue(verificationToken)
                .map(v -> v.getMobileNumber().equals(formatted) &&
                        v.getVerifiedAt() != null &&
                        v.getVerifiedAt().isAfter(Instant.now().minus(Duration.ofMinutes(30))))
                .orElse(false);
    }

    private String hashOtp(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 6) return mobile;
        int len = mobile.length();
        return mobile.substring(0, 3) + "****" + mobile.substring(len - 3);
    }
}
