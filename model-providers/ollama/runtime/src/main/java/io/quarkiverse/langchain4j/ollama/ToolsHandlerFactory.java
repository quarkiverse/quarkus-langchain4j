package io.quarkiverse.langchain4j.ollama;

public class ToolsHandlerFactory {

    private static final ToolsHandler DEFAULT = new OllamaDefaultToolsHandler();

    @SuppressWarnings("unused")
    public static ToolsHandler get(String model) {
        return DEFAULT;
    }
}
