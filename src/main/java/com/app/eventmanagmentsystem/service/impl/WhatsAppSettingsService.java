package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.config.BotsenseProperties;
import com.app.eventmanagmentsystem.model.WhatsAppNotificationSettings;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseTemplateItem;
import com.app.eventmanagmentsystem.payload.whatsapp.UpdateWhatsAppSettingsRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.WhatsAppSettingsResponse;
import com.app.eventmanagmentsystem.repository.WhatsAppNotificationSettingsRepository;
import com.app.eventmanagmentsystem.service.BotsenseApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppSettingsService {

    private final WhatsAppNotificationSettingsRepository settingsRepository;
    private final BotsenseProperties botsenseProperties;
    private final BotsenseApiClient botsenseApiClient;

    @Transactional
    public WhatsAppNotificationSettings getOrCreateSettings() {
        return settingsRepository.findActiveSettings().orElseGet(() -> {
            WhatsAppNotificationSettings initial = WhatsAppNotificationSettings.builder()
                    .integrationEnabled(false)
                    .otpVerificationEnabled(false)
                    .passActivationNotificationEnabled(false)
                    .eventReminderEnabled(false)
                    .otpTemplateName("rcf")
                    .otpTemplateLanguage("en")
                    .passActivationTemplateName("rcf_activation_pass")
                    .passActivationTemplateLanguage("en")
                    .eventReminderTemplateName("reminder_rcf")
                    .eventReminderTemplateLanguage("en")
                    .reminderLeadHours(24)
                    .updatedBy("system")
                    .build();
            return settingsRepository.save(initial);
        });
    }

    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    @Transactional(readOnly = true)
    public WhatsAppSettingsResponse getSettings() {
        WhatsAppNotificationSettings s = getOrCreateSettings();
        return toResponse(s, null, null);
    }

    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_MANAGE')")
    @Transactional
    public WhatsAppSettingsResponse updateSettings(UpdateWhatsAppSettingsRequest request, String username) {
        WhatsAppNotificationSettings s = getOrCreateSettings();

        s.setIntegrationEnabled(request.isIntegrationEnabled());
        s.setOtpVerificationEnabled(request.isOtpVerificationEnabled());
        s.setPassActivationNotificationEnabled(request.isPassActivationNotificationEnabled());
        s.setEventReminderEnabled(request.isEventReminderEnabled());

        if (request.getOtpTemplateName() != null && !request.getOtpTemplateName().isBlank()) {
            s.setOtpTemplateName(request.getOtpTemplateName().trim());
        }
        if (request.getOtpTemplateLanguage() != null && !request.getOtpTemplateLanguage().isBlank()) {
            s.setOtpTemplateLanguage(request.getOtpTemplateLanguage().trim());
        }

        if (request.getPassActivationTemplateName() != null && !request.getPassActivationTemplateName().isBlank()) {
            s.setPassActivationTemplateName(request.getPassActivationTemplateName().trim());
        }
        if (request.getPassActivationTemplateLanguage() != null && !request.getPassActivationTemplateLanguage().isBlank()) {
            s.setPassActivationTemplateLanguage(request.getPassActivationTemplateLanguage().trim());
        }

        if (request.getEventReminderTemplateName() != null && !request.getEventReminderTemplateName().isBlank()) {
            s.setEventReminderTemplateName(request.getEventReminderTemplateName().trim());
        }
        if (request.getEventReminderTemplateLanguage() != null && !request.getEventReminderTemplateLanguage().isBlank()) {
            s.setEventReminderTemplateLanguage(request.getEventReminderTemplateLanguage().trim());
        }

        if (request.getReminderLeadHours() != null) {
            s.setReminderLeadHours(request.getReminderLeadHours());
        }

        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(username != null ? username : "admin");

        WhatsAppNotificationSettings saved = settingsRepository.save(s);
        log.info("WhatsApp settings updated by {}: master={}, otp={}, activation={}, reminder={}",
                username, saved.isIntegrationEnabled(), saved.isOtpVerificationEnabled(),
                saved.isPassActivationNotificationEnabled(), saved.isEventReminderEnabled());

        return toResponse(saved, null, null);
    }

    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    public WhatsAppSettingsResponse testConnection() {
        WhatsAppNotificationSettings s = getOrCreateSettings();
        boolean connected = false;
        String errorMsg = null;

        try {
            List<Map<String, Object>> numbers = botsenseApiClient.getAccountNumbers();
            connected = !numbers.isEmpty();
        } catch (Exception e) {
            log.warn("Test connection to Botsense failed: {}", e.getMessage());
            errorMsg = e.getMessage();
        }

        return toResponse(s, connected, errorMsg);
    }

    @PreAuthorize("hasAuthority('WHATSAPP_TEMPLATE_VIEW')")
    public List<BotsenseTemplateItem> getAvailableTemplates() {
        return botsenseApiClient.listTemplates();
    }

    private WhatsAppSettingsResponse toResponse(WhatsAppNotificationSettings s, Boolean connected, String error) {
        boolean isConfigured = botsenseApiClient.isConfigured();
        return WhatsAppSettingsResponse.builder()
                .id(s.getId())
                .integrationEnabled(s.isIntegrationEnabled())
                .otpVerificationEnabled(s.isOtpVerificationEnabled())
                .passActivationNotificationEnabled(s.isPassActivationNotificationEnabled())
                .eventReminderEnabled(s.isEventReminderEnabled())
                .otpTemplateName(s.getOtpTemplateName())
                .otpTemplateLanguage(s.getOtpTemplateLanguage())
                .passActivationTemplateName(s.getPassActivationTemplateName())
                .passActivationTemplateLanguage(s.getPassActivationTemplateLanguage())
                .eventReminderTemplateName(s.getEventReminderTemplateName())
                .eventReminderTemplateLanguage(s.getEventReminderTemplateLanguage())
                .reminderLeadHours(s.getReminderLeadHours())
                .senderPhoneNumber(botsenseProperties.getSenderPhoneNumber())
                .fromNumberId(botsenseProperties.getFromNumberId())
                .configured(isConfigured)
                .connected(connected != null ? connected : isConfigured)
                .lastConnectionError(error)
                .updatedAt(s.getUpdatedAt())
                .updatedBy(s.getUpdatedBy())
                .build();
    }
}
