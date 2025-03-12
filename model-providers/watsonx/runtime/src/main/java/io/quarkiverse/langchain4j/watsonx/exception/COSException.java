package io.quarkiverse.langchain4j.watsonx.exception;

import io.quarkiverse.langchain4j.watsonx.bean.CosError;

public class COSException extends RuntimeException {

    final Integer statusCode;
    final CosError details;

    public COSException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.details = null;
    }

    public COSException(String message, CosError details, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.details = details;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public CosError details() {
        return details;
    }

    @Override
    public String toString() {
        return "COSException [statusCode=" + statusCode + ", details=" + details + ", message=" + getMessage() + "]";
    }
}
