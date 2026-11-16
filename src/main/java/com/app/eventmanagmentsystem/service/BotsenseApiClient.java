package com.app.eventmanagmentsystem.service;

import com.app.eventmanagmentsystem.config.BotsenseProperties;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseMessageRequest;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseMessageResponse;
import com.app.eventmanagmentsystem.payload.whatsapp.BotsenseTemplateItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class BotsenseApiClient {

    private final BotsenseProperties properties;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public BotsenseApiClient(BotsenseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public BotsenseApiClient(BotsenseProperties properties) {
        this(properties, new ObjectMapper());
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + properties.getApiKey());
        headers.set("Accept", "application/json");
        return headers;
    }

    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && properties.getFromNumberId() != null && !properties.getFromNumberId().isBlank();
    }

    /**
     * GET /v1/account/numbers
     * Tests connectivity and retrieves connected numbers.
     */
    public List<Map<String, Object>> getAccountNumbers() {
        if (!isConfigured()) {
            throw new IllegalStateException("Botsense API credentials not configured.");
        }

        String url = properties.getBaseUrl() + "/account/numbers";
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().get("data") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                return data;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Botsense getAccountNumbers error: {}", e.getMessage());
            throw new RuntimeException("Failed to reach Botsense API: " + e.getMessage(), e);
        }
    }

    /**
     * GET /v1/templates
     * Retrieves all approved WhatsApp templates.
     */
    public List<BotsenseTemplateItem> listTemplates() {
        if (!isConfigured()) {
            return Collections.emptyList();
        }

        String url = properties.getBaseUrl() + "/templates";
        HttpHeaders headers = createHeaders();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            List<BotsenseTemplateItem> templates = new ArrayList<>();
            if (response.getBody() != null && response.getBody().get("data") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.getBody().get("data");
                for (Map<String, Object> map : dataList) {
                    templates.add(mapToTemplate(map));
                }
            }
            return templates;
        } catch (Exception e) {
            log.error("Botsense listTemplates error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private BotsenseTemplateItem mapToTemplate(Map<String, Object> map) {
        return BotsenseTemplateItem.builder()
                .id(map.get("id") != null ? map.get("id").toString() : null)
                .name(map.get("name") != null ? map.get("name").toString() : null)
                .category(map.get("category") != null ? map.get("category").toString() : null)
                .language(map.get("language") != null ? map.get("language").toString() : null)
                .status(map.get("status") != null ? map.get("status").toString() : null)
                .templateType(map.get("template_type") != null ? map.get("template_type").toString() : null)
                .body(map.get("body") != null ? map.get("body").toString() : null)
                .header(map.get("header") != null ? map.get("header").toString() : null)
                .headerType(map.get("header_type") != null ? map.get("header_type").toString() : null)
                .footer(map.get("footer") != null ? map.get("footer").toString() : null)
                .buttons(map.get("buttons") instanceof List ? (List<Map<String, Object>>) map.get("buttons") : null)
                .components(map.get("components") instanceof List ? (List<Map<String, Object>>) map.get("components") : null)
                .build();
    }

    /**
     * Send Authentication OTP Template (template name e.g. "rcf")
     * Body variable {{1}} = code, with copy code button
     */
    public BotsenseMessageResponse sendOtpTemplate(String to, String code, String templateName, String language) {
        String cleanPhone = formatPhoneNumber(to);
        String lang = (language != null && !language.isBlank()) ? language : "en";
        String tpl = (templateName != null && !templateName.isBlank()) ? templateName : "rcf";

        BotsenseMessageRequest.Component bodyComponent = BotsenseMessageRequest.Component.builder()
                .type("body")
                .parameters(List.of(
                        BotsenseMessageRequest.Parameter.builder()
                                .type("text")
                                .text(code)
                                .build()
                ))
                .build();

        BotsenseMessageRequest.Component buttonComponent = BotsenseMessageRequest.Component.builder()
                .type("button")
                .subType("url")
                .index(0)
                .parameters(List.of(
                        BotsenseMessageRequest.Parameter.builder()
                                .type("text")
                                .text(code)
                                .build()
                ))
                .build();

        BotsenseMessageRequest request = BotsenseMessageRequest.builder()
                .to(cleanPhone)
                .fromNumberId(properties.getFromNumberId())
                .type("template")
                .template(BotsenseMessageRequest.TemplateContent.builder()
                        .name(tpl)
                        .language(lang)
                        .components(List.of(bodyComponent, buttonComponent))
                        .build())
                .build();

        return sendRawMessage(request);
    }

    /**
     * Send arbitrary Template Message
     */
    public BotsenseMessageResponse sendTemplateMessage(String to, String templateName, String language,
                                                       List<BotsenseMessageRequest.Component> components) {
        String cleanPhone = formatPhoneNumber(to);
        String lang = (language != null && !language.isBlank()) ? language : "en";

        BotsenseMessageRequest request = BotsenseMessageRequest.builder()
                .to(cleanPhone)
                .fromNumberId(properties.getFromNumberId())
                .type("template")
                .template(BotsenseMessageRequest.TemplateContent.builder()
                        .name(templateName)
                        .language(lang)
                        .components(components)
                        .build())
                .build();

        return sendRawMessage(request);
    }

    /**
     * Send plain text message
     */
    public BotsenseMessageResponse sendTextMessage(String to, String text) {
        String cleanPhone = formatPhoneNumber(to);
        BotsenseMessageRequest request = BotsenseMessageRequest.builder()
                .to(cleanPhone)
                .fromNumberId(properties.getFromNumberId())
                .type("text")
                .text(BotsenseMessageRequest.TextContent.builder().body(text).build())
                .build();

        return sendRawMessage(request);
    }

    /**
     * Send Document Message (Pass PDF)
     */
    public BotsenseMessageResponse sendDocumentMessage(String to, String mediaId, String publicLink,
                                                        String filename, String caption) {
        String cleanPhone = formatPhoneNumber(to);

        BotsenseMessageRequest.DocumentContent doc = BotsenseMessageRequest.DocumentContent.builder()
                .id(mediaId)
                .link(publicLink)
                .filename(filename != null ? filename : "Event-Pass.pdf")
                .caption(caption != null ? caption : "Your official event pass")
                .build();

        BotsenseMessageRequest request = BotsenseMessageRequest.builder()
                .to(cleanPhone)
                .fromNumberId(properties.getFromNumberId())
                .type("document")
                .document(doc)
                .build();

        return sendRawMessage(request);
    }

    /**
     * Upload binary media (e.g. PDF) to Botsense POST /v1/media
     * Returns media ID
     */
    public String uploadMedia(byte[] fileBytes, String filename, String mimeType) {
        if (!isConfigured()) {
            throw new IllegalStateException("Botsense API is not configured.");
        }

        String url = properties.getBaseUrl() + "/media";
        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename != null ? filename : "document.pdf";
            }
        };
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().get("id") != null) {
                return response.getBody().get("id").toString();
            }
            throw new RuntimeException("Media upload response did not include media ID");
        } catch (Exception e) {
            log.error("Botsense media upload error: {}", e.getMessage());
            throw new RuntimeException("Failed to upload media to Botsense: " + e.getMessage(), e);
        }
    }

    private BotsenseMessageResponse sendRawMessage(BotsenseMessageRequest request) {
        if (!isConfigured()) {
            log.warn("Botsense API key or from_number_id not configured — suppressing outbound message to {}", request.getTo());
            return BotsenseMessageResponse.builder()
                    .object("error")
                    .error(new BotsenseMessageResponse.ErrorDetails("Botsense API not configured", "CONFIGURATION_ERROR", 500, null))
                    .build();
        }

        String url = properties.getBaseUrl() + "/messages";
        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<BotsenseMessageRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<BotsenseMessageResponse> response = getRestTemplate().exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    BotsenseMessageResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.error("Botsense message send HTTP error [{}] to {}: {}", statusCode, request.getTo(), responseBody);

            BotsenseMessageResponse errorResponse = null;
            if (responseBody != null && !responseBody.isBlank()) {
                try {
                    errorResponse = objectMapper.readValue(responseBody, BotsenseMessageResponse.class);
                } catch (Exception parseEx) {
                    log.debug("Could not parse Botsense error response as BotsenseMessageResponse: {}", parseEx.getMessage());
                }
            }

            if (errorResponse != null && errorResponse.getError() != null) {
                errorResponse.getError().setHttpStatus(statusCode);
                return errorResponse;
            }

            String errorType = e.getStatusCode().is4xxClientError() ? "CLIENT_ERROR" : "SERVER_ERROR";
            String code = e.getStatusCode().is4xxClientError() ? "BAD_REQUEST" : "SERVER_ERROR";
            return BotsenseMessageResponse.builder()
                    .object("error")
                    .error(new BotsenseMessageResponse.ErrorDetails(
                            responseBody != null && !responseBody.isBlank() ? responseBody : e.getMessage(),
                            errorType,
                            statusCode,
                            code
                    ))
                    .build();
        } catch (ResourceAccessException e) {
            log.error("Botsense connection/timeout error to {}: {}", request.getTo(), e.getMessage());
            return BotsenseMessageResponse.builder()
                    .object("error")
                    .error(new BotsenseMessageResponse.ErrorDetails(e.getMessage(), "TIMEOUT_ERROR", 504, "NETWORK_TIMEOUT"))
                    .build();
        } catch (Exception e) {
            log.error("Botsense message send error to {}: {}", request.getTo(), e.getMessage());
            return BotsenseMessageResponse.builder()
                    .object("error")
                    .error(new BotsenseMessageResponse.ErrorDetails(e.getMessage(), "UNEXPECTED_ERROR", 500, "INTERNAL_ERROR"))
                    .build();
        }
    }

    public static String formatPhoneNumber(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        // If 10 digits without country code, default to India +91
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (!digits.startsWith("+")) {
            return "+" + digits;
        }
        return digits;
    }
}
