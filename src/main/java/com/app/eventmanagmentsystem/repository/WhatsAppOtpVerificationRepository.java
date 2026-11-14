package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.WhatsAppOtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppOtpVerificationRepository extends JpaRepository<WhatsAppOtpVerification, UUID> {

    @Query("SELECT v FROM WhatsAppOtpVerification v WHERE v.mobileNumber = :mobile AND v.verified = false AND v.expiresAt > :now ORDER BY v.createdAt DESC LIMIT 1")
    Optional<WhatsAppOtpVerification> findActivePendingVerification(
            @Param("mobile") String mobile,
            @Param("now") Instant now);

    Optional<WhatsAppOtpVerification> findByVerificationTokenAndVerifiedTrue(String verificationToken);

    long countByMobileNumberAndCreatedAtAfter(String mobileNumber, Instant after);
}
