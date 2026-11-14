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
        private Language language;
        private List<Component> components;
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
