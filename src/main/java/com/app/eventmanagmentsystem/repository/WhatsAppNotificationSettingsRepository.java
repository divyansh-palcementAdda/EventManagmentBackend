package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.WhatsAppNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppNotificationSettingsRepository extends JpaRepository<WhatsAppNotificationSettings, UUID> {

    @Query("SELECT s FROM WhatsAppNotificationSettings s ORDER BY s.createdAt ASC LIMIT 1")
    Optional<WhatsAppNotificationSettings> findActiveSettings();
}
