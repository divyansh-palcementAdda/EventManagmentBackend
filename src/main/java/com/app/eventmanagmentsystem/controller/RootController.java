package com.app.eventmanagmentsystem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "System Info", description = "System health, API metadata and status")
public class RootController {

    @Operation(summary = "API Root / Status Ping", description = "Provides service heartbeat and API entry information.")
    @GetMapping({"/", "/api/v1", "/api/v1/health"})
    public ResponseEntity<Map<String, Object>> getApiStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "EventPass Management System API");
        status.put("version", "v1");
        status.put("status", "UP");
        status.put("timestamp", Instant.now().toString());
        status.put("documentation", "/swagger-ui.html");
        return ResponseEntity.ok(status);
    }
}

