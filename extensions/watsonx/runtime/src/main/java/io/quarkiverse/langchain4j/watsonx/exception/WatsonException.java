package io.quarkiverse.langchain4j.watsonx.exception;

import io.quarkiverse.langchain4j.watsonx.bean.WatsonError;

public class WatsonException extends RuntimeException {

    Integer statusCode;
    WatsonError details;

    public WatsonException(String message, Integer statusCode, WatsonError details) {
        super(message);
        this.statusCode = statusCode;
        this.details = details;
    }

    public WatsonException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public WatsonException(Integer statusCode, WatsonError details) {
        this.statusCode = statusCode;
        this.details = details;
    }

    public WatsonException(Throwable cause, Integer statusCode) {
        super(cause.getMessage(), cause);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public WatsonError details() {
        return details;
    }

    @Override
    public String toString() {
        return "WatsonException [statusCode=" + statusCode + ", details=" + details + "]";
    }
}