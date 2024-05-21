package io.quarkiverse.langchain4j.watsonx.bean;

public record TokenizationResponse(Result result) {

    public record Result(int tokenCount) {

    }
}
