package io.quarkiverse.langchain4j.watsonx.exception;

public class TextExtractionException extends Exception {

    final String code;

    public TextExtractionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public TextExtractionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "TextExtractionException [code=" + code + ", message=" + getMessage() + "]";
    }
}
