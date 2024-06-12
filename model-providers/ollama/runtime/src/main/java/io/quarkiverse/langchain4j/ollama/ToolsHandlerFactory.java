package io.quarkiverse.langchain4j.ollama;

import io.quarkiverse.langchain4j.ollama.toolshandler.Llama3ToolsHandler;

public class ToolsHandlerFactory {

    private static final ToolsHandler LLAMA3 = new Llama3ToolsHandler();

    public static ToolsHandler get(String model) {
        return LLAMA3;
    }
}
