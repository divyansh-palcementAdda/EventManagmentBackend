package com.app.eventmanagmentsystem.model.audit;

import com.app.eventmanagmentsystem.security.AppUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current auditor identifier to Spring Data auditing.
 *
 * The "auditor" value stored in {@code created_by_id} / {@code updated_by_id}
 * is the string representation of the authenticated user's UUID.
 * For system/background operations where no HTTP request exists, the auditor
 * falls back to "SYSTEM".
 */
@Component("auditorAware")
public class SecurityAuditorAware implements AuditorAware<String> {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.of(SYSTEM_ACTOR);
        }
        if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
            return Optional.of(userDetails.getUserId().toString());
        }
        return Optional.of(SYSTEM_ACTOR);
    }
}

