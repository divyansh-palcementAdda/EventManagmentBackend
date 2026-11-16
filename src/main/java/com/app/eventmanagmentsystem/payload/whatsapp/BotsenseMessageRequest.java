package com.app.eventmanagmentsystem.payload.whatsapp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BotsenseMessageRequest {

    private String to;

    @JsonProperty("from_number_id")
    private String fromNumberId;

    private String type; // "text", "template", "document"

    private TextContent text;

    private TemplateContent template;

    private DocumentContent document;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextContent {
        private String body;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplateContent {
        private String name;
        private String language;
        private List<Component> components;

        public void setLanguage(String language) {
            this.language = language;
        }

        public void setLanguage(Language lang) {
            this.language = lang != null ? lang.getCode() : null;
        }

        @com.fasterxml.jackson.annotation.JsonSetter("language")
        public void setLanguageFromJson(com.fasterxml.jackson.databind.JsonNode node) {
            if (node == null || node.isNull()) {
                this.language = null;
            } else if (node.isTextual()) {
                this.language = node.asText();
            } else if (node.has("code")) {
                this.language = node.get("code").asText();
            } else {
                this.language = node.asText();
            }
        }

        public static class TemplateContentBuilder {
            private String language;

            public TemplateContentBuilder language(String language) {
                this.language = language;
                return this;
            }

            public TemplateContentBuilder language(Language lang) {
                this.language = lang != null ? lang.getCode() : null;
                return this;
            }
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Language {
        private String code;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Component {
        private String type; // "header", "body", "button"

        @JsonProperty("sub_type")
        private String subType; // "url" for auth button

        private Integer index;

        private List<Parameter> parameters;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameter {
        private String type; // "text", "image", "document"
        private String text;
        private DocumentContent document;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentContent {
        private String id;
        private String link;
        private String caption;
        private String filename;
    }
}
