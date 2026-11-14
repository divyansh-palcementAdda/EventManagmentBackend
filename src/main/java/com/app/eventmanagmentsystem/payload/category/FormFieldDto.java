package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.FieldType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormFieldDto {
    private UUID id;

    @NotBlank(message = "Field key is required")
    private String fieldKey;

    @NotBlank(message = "Field label is required")
    private String label;

    private String description;
    private String placeholder;

    @NotNull(message = "Field type is required")
    private FieldType fieldType;

    private boolean required;
    @Builder.Default
    private boolean visible = true;
    private int displayOrder;
    private String defaultValue;
    private JsonNode validationRules;
    private List<String> options;
    @Builder.Default
    private boolean active = true;

    @JsonSetter("validationRules")
    public void setValidationRulesFromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            this.validationRules = null;
        } else {
            this.validationRules = node;
        }
    }

    public void setValidationRules(String rules) {
        if (rules == null || rules.isBlank()) {
            this.validationRules = null;
        } else {
            try {
                this.validationRules = new ObjectMapper().readTree(rules);
            } catch (Exception e) {
                this.validationRules = new TextNode(rules);
            }
        }
    }

    public void setValidationRules(JsonNode node) {
        this.validationRules = node;
    }

    @JsonSetter("options")
    public void setOptionsFromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            this.options = null;
        } else if (node.isArray()) {
            List<String> list = new ArrayList<>();
            node.forEach(n -> {
                String val = n.asText();
                if (val != null && !val.trim().isEmpty()) {
                    list.add(val.trim());
                }
            });
            this.options = list;
        } else if (node.isTextual()) {
            String text = node.asText();
            if (text.isBlank()) {
                this.options = Collections.emptyList();
            } else {
                this.options = Arrays.stream(text.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
        } else {
            this.options = Collections.emptyList();
        }
    }

    public void setOptions(String optionsStr) {
        if (optionsStr == null || optionsStr.isBlank()) {
            this.options = null;
        } else {
            this.options = Arrays.stream(optionsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    public void setOptions(List<String> optionsList) {
        this.options = optionsList;
    }

    public static class FormFieldDtoBuilder {
        public FormFieldDtoBuilder options(String optionsStr) {
            if (optionsStr == null || optionsStr.isBlank()) {
                this.options = null;
            } else {
                this.options = Arrays.stream(optionsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return this;
        }

        public FormFieldDtoBuilder options(List<String> optionsList) {
            this.options = optionsList;
            return this;
        }

        public FormFieldDtoBuilder validationRules(String rules) {
            if (rules == null || rules.isBlank()) {
                this.validationRules = null;
            } else {
                try {
                    this.validationRules = new ObjectMapper().readTree(rules);
                } catch (Exception e) {
                    this.validationRules = new TextNode(rules);
                }
            }
            return this;
        }

        public FormFieldDtoBuilder validationRules(JsonNode node) {
            this.validationRules = node;
            return this;
        }
    }
}
