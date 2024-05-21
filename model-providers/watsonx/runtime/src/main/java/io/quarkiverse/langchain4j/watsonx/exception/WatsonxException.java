package io.quarkiverse.langchain4j.watsonx.exception;

import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;

public class WatsonxException extends RuntimeException {

    final Integer statusCode;
    final WatsonxError details;

    public WatsonxException(String message, Integer statusCode, WatsonxError details) {
        super(message);
        this.statusCode = statusCode;
        this.details = details;
    }

    public WatsonxException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.details = null;
    }

    public WatsonxException(Integer statusCode, WatsonxError details) {
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

    public WatsonxError details() {
        return details;
    }

    @Override
    public String toString() {
        return "WatsonException [statusCode=" + statusCode + ", details=" + details + "]";
    }
}