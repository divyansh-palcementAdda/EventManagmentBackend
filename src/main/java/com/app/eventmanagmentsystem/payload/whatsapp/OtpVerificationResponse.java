package com.app.eventmanagmentsystem.payload.whatsapp;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerificationResponse {

    private boolean verified;
    private String verificationToken;
    private String message;
    private Instant expiresAt;
}
