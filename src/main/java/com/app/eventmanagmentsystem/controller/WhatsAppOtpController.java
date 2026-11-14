package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.OtpVerificationResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.SendOtpRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.VerifyOtpRequest;
import com.app.eventmanagmentsystem.service.impl.WhatsAppOtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "WhatsApp OTP Verification", description = "Public endpoints for attendee WhatsApp phone verification during pass registration and activation")
@RestController
@RequestMapping("/api/v1/whatsapp/otp")
@RequiredArgsConstructor
public class WhatsAppOtpController {

    private final WhatsAppOtpService otpService;

    @Operation(summary = "Check if WhatsApp OTP is required", description = "Returns whether WhatsApp OTP verification is currently required by policy for an event.")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOtpStatus(@RequestParam(required = false) UUID eventId) {
        boolean required = otpService.isOtpRequired(eventId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "otpRequired", required,
                "provider", "BOTSENSE"
        )));
    }

    @Operation(summary = "Send WhatsApp OTP", description = "Dispatches a 6-digit verification code to the attendee's WhatsApp number.")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        OtpVerificationResponse response = otpService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @Operation(summary = "Verify WhatsApp OTP", description = "Verifies the submitted 6-digit code and returns a one-time verification token for pass activation.")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        OtpVerificationResponse response = otpService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }
}
