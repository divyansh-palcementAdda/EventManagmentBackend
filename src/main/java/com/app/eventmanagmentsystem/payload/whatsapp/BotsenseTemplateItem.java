package com.app.eventmanagmentsystem.payload.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BotsenseTemplateItem {

    private String id;
    private String name;
    private String category;
    private String language;
    private String status;

    @JsonProperty("template_type")
    private String templateType;

    private String body;
    private String header;

    @JsonProperty("header_type")
    private String headerType;

    private String footer;

    private List<Map<String, Object>> buttons;
    private List<Map<String, Object>> components;
}
