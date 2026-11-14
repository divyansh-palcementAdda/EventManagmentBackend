package com.app.eventmanagmentsystem.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_otp_verifications", indexes = {
        @Index(name = "idx_wov_mobile_event", columnList = "mobile_number, event_id"),
        @Index(name = "idx_wov_token", columnList = "verification_token"),
        @Index(name = "idx_wov_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppOtpVerification {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "mobile_number", nullable = false, length = 20)
    private String mobileNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pass_id")
    private EventPass pass;

    /**
     * SHA-256 hash of the generated 6-digit OTP. Never stored plaintext.
     */
    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    /**
     * Signed/random token issued upon successful OTP verification.
     * Submitted by client during pass activation.
     */
    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "resend_count", nullable = false)
    @Builder.Default
    private int resendCount = 0;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
