package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WatsonxError(Integer statusCode, String trace, List<Error> errors) {

    public static record Error(String code, String message) {
        public Optional<Code> codeToEnum() {
            try {
                return Optional.of(Code.valueOf(code.toUpperCase()));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    public static enum Code {

        @JsonProperty("authorization_rejected")
        AUTHORIZATION_REJECTED,

        @JsonProperty("json_type_error")
        JSON_TYPE_ERROR,

        @JsonProperty("model_not_supported")
        MODEL_NOT_SUPPORTED,

        @JsonProperty("model_no_support_for_function")
        MODEL_NO_SUPPORT_FOR_FUNCTION,

        @JsonProperty("user_authorization_failed")
        USER_AUTHORIZATION_FAILED,

        @JsonProperty("json_validation_error")
        JSON_VALIDATION_ERROR,

        @JsonProperty("invalid_request_entity")
        INVALID_REQUEST_ENTITY,

        @JsonProperty("invalid_input_argument")
        INVALID_INPUT_ARGUMENT,

        @JsonProperty("token_quota_reached")
        TOKEN_QUOTA_REACHED,

        @JsonProperty("authentication_token_expired")
        AUTHENTICATION_TOKEN_EXPIRED,

        @JsonProperty("text_extraction_event_does_not_exist")
        TEXT_EXTRACTION_EVENT_DOES_NOT_EXIST;
    }
}
