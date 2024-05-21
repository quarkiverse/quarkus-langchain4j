package io.quarkiverse.langchain4j.openai;

public class OpenAiApiException extends RuntimeException {

    public OpenAiApiException(Class<?> responseClass) {
        super("OpenAI REST API return an error object as a response so no conversion to '" + responseClass.getSimpleName()
                + "' was possible");
    }

}
