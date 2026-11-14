package com.app.eventmanagmentsystem.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.botsense")
@Getter
@Setter
public class BotsenseProperties {

    /**
     * Botsense API Base URL
     * Default: https://app.botsense.io/api/v1
     */
    private String baseUrl = "https://app.botsense.io/api/v1";

    /**
     * Bearer API Key provided by Botsense
     */
    private String apiKey = "";

    /**
     * UUID from_number_id for Renaissance University
     * Confirmed: f401f874-6837-4091-b6a9-76d5c8031b21
     */
    private String fromNumberId = "f401f874-6837-4091-b6a9-76d5c8031b21";

    /**
     * WABA ID: 1627238105458579
     */
    private String wabaId = "1627238105458579";

    /**
     * Sender phone number: 919039035431
     */
    private String senderPhoneNumber = "919039035431";

    /**
     * Connection timeout in milliseconds
     */
    private int connectTimeoutMs = 10000;

    /**
     * Read timeout in milliseconds
     */
    private int readTimeoutMs = 15000;
}
