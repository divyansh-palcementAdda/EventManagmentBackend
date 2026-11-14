package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.WhatsAppNotificationSettings;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final WhatsAppSettingsService settingsService;
    private final WhatsAppNotificationService notificationService;
    private final EventRepository eventRepository;
    private final EventPassRepository passRepository;

    /**
     * Periodic reminder scanner.
     * Runs every hour to check for upcoming active events within lead hours.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void scanAndEnqueueReminders() {
        WhatsAppNotificationSettings settings = settingsService.getOrCreateSettings();
        if (!settings.isIntegrationEnabled() || !settings.isEventReminderEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Instant windowEnd = now.plus(Duration.ofHours(settings.getReminderLeadHours()));

        List<Event> upcomingEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .filter(e -> e.getStartsAt() != null && e.getStartsAt().isAfter(now) && e.getStartsAt().isBefore(windowEnd))
                .toList();

        for (Event event : upcomingEvents) {
            long daysRemaining = Math.max(1, Duration.between(now, event.getStartsAt()).toDays());

            List<EventPass> activatedPasses = passRepository.findByEventIdAndStatusOrderBySequenceNumberAsc(
                    event.getId(), PassStatus.ACTIVATED);

            for (EventPass pass : activatedPasses) {
                if (pass.getAttendee() != null && pass.getAttendee().getMobileNumber() != null) {
                    notificationService.enqueueEventReminder(event, pass.getAttendee(), pass, (int) daysRemaining);
                }
            }
        }
    }
}
