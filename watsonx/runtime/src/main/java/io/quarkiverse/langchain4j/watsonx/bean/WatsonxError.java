package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WatsonxError(Integer statusCode, String trace, List<Error> errors) {

    public static record Error(Code code, String message) {
    }

    public static enum Code {

        @JsonProperty("authorization_rejected")
        AUTHORIZATION_REJECTED,

        @JsonProperty("model_not_supported")
        MODEL_NOT_SUPPORTED,

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
        AUTHENTICATION_TOKEN_EXPIRED;
    }
}
