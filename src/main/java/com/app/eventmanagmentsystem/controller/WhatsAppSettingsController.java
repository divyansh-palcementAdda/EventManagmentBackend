package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseTemplateItem;
import com.app.eventmanagmentsystem.payload.whatsapp.UpdateWhatsAppSettingsRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.WhatsAppSettingsResponse;
import com.app.eventmanagmentsystem.service.impl.WhatsAppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "WhatsApp Settings", description = "Endpoints for managing Botsense WhatsApp integration settings, independent feature toggles, and templates")
@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
public class WhatsAppSettingsController {

    private final WhatsAppSettingsService settingsService;

    @Operation(summary = "Get WhatsApp integration settings", description = "Retrieves master and independent feature toggles (OTP, Pass Activation, Reminders) and template mappings.")
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getSettings()));
    }

    @Operation(summary = "Update WhatsApp integration settings", description = "Updates master toggle and independent feature toggles. Requires WHATSAPP_CONFIG_MANAGE.")
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_MANAGE')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateWhatsAppSettingsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "admin";
        WhatsAppSettingsResponse response = settingsService.updateSettings(request, username);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings updated successfully", response));
    }

    @Operation(summary = "Test Botsense WhatsApp API connection", description = "Tests connection to Botsense API and verifies connected phone numbers.")
    @PostMapping("/test-connection")
    @PreAuthorize("hasAuthority('WHATSAPP_CONFIG_VIEW')")
    public ResponseEntity<ApiResponse<WhatsAppSettingsResponse>> testConnection() {
        return ResponseEntity.ok(ApiResponse.success("Connection test completed", settingsService.testConnection()));
    }

    @Operation(summary = "List approved WhatsApp templates", description = "Retrieves all approved templates from Botsense for admin mapping.")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('WHATSAPP_TEMPLATE_VIEW')")
    public ResponseEntity<ApiResponse<List<BotsenseTemplateItem>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getAvailableTemplates()));
    }
}
