package io.quarkiverse.langchain4j.bam;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Feature;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class contains the details of an error that you may encounter when
 * performing operations on the BAM API.
 * <p>
 * For more information see details at
 * {@link https://bam.res.ibm.com/docs/api-reference#errors}
 */
public class BamException extends RuntimeException {

    @JsonProperty("status_code")
    Integer statusCode;
    String error;
    String message;
    Optional<Extensions> extensions;

    public BamException() {
    }

    public BamException(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Optional<Extensions> getExtensions() {
        return extensions;
    }

    public void setExtensions(Optional<Extensions> extensions) {
        this.extensions = extensions;
    }

    @Override
    public String toString() {
        return "BamHttpException [statusCode=" + statusCode + ", error=" + error + ", message=" + message + ", extensions="
                + extensions + "]";
    }

    /**
     * Additional information about the error, if available.
     * <p>
     * For more information see details at
     * {@link https://bam.res.ibm.com/docs/api-reference#errors}
     */
    public static class Extensions {

        Code code;
        Reason reason;
        @JsonFormat(with = Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        Optional<List<Map<String, Object>>> state;

        public Code getCode() {
            return code;
        }

        public Reason getReason() {
            return reason;
        }

        public Optional<List<Map<String, Object>>> getState() {
            return state;
        }

        @Override
        public String toString() {
            return "Extensions [code=" + code + ", reason=" + reason + ", state=" + state + "]";
        }
    }

    public static enum Code {

        /*
         * An error happened during authentication
         */
        AUTH_ERROR("AUTH_ERROR"),

        /*
         * An unspecified error occurred
         */
        INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR"),

        /*
         * Provided input was invalid
         */
        INVALID_INPUT("INVALID_INPUT"),

        /*
         * Requested resource was not found
         */
        NOT_FOUND("NOT_FOUND"),

        /*
         * An unspecified service error occurred. This is usually not a problem with the
         * API, but with one of the underlying services related to language models
         */
        SERVICE_ERROR("SERVICE_ERROR"),

        /*
         * The underlying language model service is not available. It can be busy or
         * simply not reachable
         */
        SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE"),

        /*
         * The user has sent too many requests in a given amount of time
         */
        TOO_MANY_REQUESTS("TOO_MANY_REQUESTS");

        private String value;

        private Code(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    public static enum Reason {

        /*
         * Invalid API key or invalid information decoded from JWT token
         */
        INVALID_AUTHORIZATION("INVALID_AUTHORIZATION"),

        /*
         * The identity of an authenticated user is invalid
         */
        INVALID_IDENTITY("INVALID_IDENTITY"),

        /*
         * JWT token has expired
         */
        TOKEN_EXPIRED("TOKEN_EXPIRED"),

        /*
         * JWT token cannot be decoded or validated
         */
        TOKEN_INVALID("TOKEN_INVALID"),

        /*
         * Trying to access a resource that requires the user to accept Terms of use
         */
        TOU_NOT_ACCEPTED("TOU_NOT_ACCEPTED");

        private String value;

        Reason(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }
}
