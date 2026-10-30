package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * System user — administrators and event staff.
 * Attendees are stored separately in {@link Attendee}.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"passwordHash", "roles"})
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 180)
    private String email;

    /** BCrypt hash — never exposed through APIs. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    /** Convenience: normalize email to lowercase on set. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase().trim();
    }

    public boolean isEnabled() {
        return AccountStatus.ACTIVE.equals(this.accountStatus);
    }
}

