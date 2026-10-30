package com.app.eventmanagmentsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing with our {@link com.app.eventmanagmentsystem.model.audit.SecurityAuditorAware}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {
    // The SecurityAuditorAware bean is registered via @Component("auditorAware")
}

