package io.quarkiverse.langchain4j.watsonx.bean;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IAMError(

        @JsonProperty("errorCode") Code errorCode,
        @JsonProperty("errorMessage") String errorMessage) {

    public static enum Code {

        // Provided API key could not be found
        BXNIM0415E
    }
}
