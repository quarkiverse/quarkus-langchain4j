package io.quarkiverse.langchain4j.openai.common;

public class OpenAiApiException extends RuntimeException {

    private final String errorBody;

    public OpenAiApiException(Class<?> responseClass) {
        super("OpenAI REST API return an error object as a response so no conversion to '" + responseClass.getSimpleName()
                + "' was possible");
        this.errorBody = null;
    }

    /**
     * @param errorBody what the API returned instead of a valid response, for example
     *        {@code {"error":{"message":"...","type":"...","code":"..."}}}
     */
    public OpenAiApiException(Class<?> responseClass, String errorBody) {
        super("OpenAI REST API returned a response that could not be converted to '" + responseClass.getSimpleName()
                + "': " + errorBody);
        this.errorBody = errorBody;
    }

    /**
     * What the API returned instead of a valid response, usually an {@code error} object, or {@code null} if it is not
     * known.
     */
    public String errorBody() {
        return errorBody;
    }

}
