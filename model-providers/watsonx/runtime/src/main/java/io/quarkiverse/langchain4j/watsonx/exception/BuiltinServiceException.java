package io.quarkiverse.langchain4j.watsonx.exception;

public class BuiltinServiceException extends RuntimeException {

    final Integer statusCode;
    final String details;

    public BuiltinServiceException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.details = message;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String details() {
        return details;
    }

    @Override
    public String toString() {
        return "BuiltinToolException [statusCode=" + statusCode + ", details=" + details + "]";
    }
}