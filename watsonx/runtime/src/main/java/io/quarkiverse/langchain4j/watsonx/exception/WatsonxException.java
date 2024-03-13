package io.quarkiverse.langchain4j.watsonx.exception;

import io.quarkiverse.langchain4j.watsonx.bean.WatsonError;

public class WatsonxException extends RuntimeException {

    final Integer statusCode;
    final WatsonError details;

    public WatsonxException(String message, Integer statusCode, WatsonError details) {
        super(message);
        this.statusCode = statusCode;
        this.details = details;
    }

    public WatsonxException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.details = null;
    }

    public WatsonxException(Integer statusCode, WatsonError details) {
        this.statusCode = statusCode;
        this.details = details;
    }

    public WatsonxException(Throwable cause, Integer statusCode) {
        super(cause.getMessage(), cause);
        this.statusCode = statusCode;
        this.details = null;
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