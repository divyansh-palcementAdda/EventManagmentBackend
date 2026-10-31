package com.app.eventmanagmentsystem.security;

import com.app.eventmanagmentsystem.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails wrapper around a system {@link User}.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final boolean enabled;
    private final Set<String> roles;
    private final Set<GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.enabled = user.isEnabled();

        this.roles = user.getRoles() != null
                ? user.getRoles().stream()
                        .map(r -> r.getName() != null ? r.getName().trim() : "")
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toUnmodifiableSet())
                : Collections.emptySet();

        Set<GrantedAuthority> auths = new HashSet<>();
        if (user.getRoles() != null) {
            for (var role : user.getRoles()) {
                if (role.getName() != null && !role.getName().isBlank()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + role.getName().trim()));
                }
                if (role.getPermissions() != null) {
                    for (var perm : role.getPermissions()) {
                        if (perm.getName() != null && !perm.getName().isBlank()) {
                            auths.add(new SimpleGrantedAuthority(perm.getName().trim()));
                        }
                    }
                }
            }
        }
        this.authorities = Collections.unmodifiableSet(auths);
    }

    public boolean isSuperAdmin() {
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase("SUPER_ADMIN"));
    }

    public boolean hasRole(String roleName) {
        if (roleName == null) return false;
        return roles.stream().anyMatch(r -> r.equalsIgnoreCase(roleName.trim()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

