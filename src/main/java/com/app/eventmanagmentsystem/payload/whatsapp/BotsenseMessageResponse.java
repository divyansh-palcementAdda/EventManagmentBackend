package com.app.eventmanagmentsystem.payload.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BotsenseMessageResponse {

    private String object;

    private List<MessageRef> messages;

    private ErrorDetails error;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageRef {
        private String id;
        private String status;

        @JsonProperty("message_status")
        private String messageStatus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetails {
        private String message;
        private String type;
        private String code;
        private String param;

        @JsonProperty("error_subcode")
        private Integer errorSubcode;

        private Integer httpStatus;

        public ErrorDetails(String message, String type, Integer httpStatus, String code) {
            this.message = message;
            this.type = type;
            this.httpStatus = httpStatus;
            this.code = code;
        }

        public ErrorDetails(String message, String type, Integer httpStatus) {
            this(message, type, httpStatus, null);
        }
    }

    public String resolveMessageId() {
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0).getId();
        }
        return null;
    }
}
