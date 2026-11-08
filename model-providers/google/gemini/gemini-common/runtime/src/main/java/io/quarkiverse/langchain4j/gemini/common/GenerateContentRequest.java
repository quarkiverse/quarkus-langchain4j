package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
        ToolConfig toolConfig, GenerationConfig generationConfig,
        @JsonInclude(JsonInclude.Include.NON_NULL) ModelArmorConfig modelArmorConfig) {

    public GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
            ToolConfig toolConfig, GenerationConfig generationConfig) {
        this(contents, systemInstruction, tools, toolConfig, generationConfig, null);
    }

    public GenerateContentRequest withModelArmorConfig(ModelArmorConfig modelArmorConfig) {
        return new GenerateContentRequest(contents, systemInstruction, tools, toolConfig, generationConfig,
                modelArmorConfig);
    }

    public record SystemInstruction(List<Part> parts) {

        public static SystemInstruction ofContent(List<String> contents) {
            return new SystemInstruction(contents.stream().map(Part::new).toList());
        }

        public record Part(String text) {

        }
    }

    public record Tool(
            List<FunctionDeclaration> functionDeclarations,
            @JsonProperty("google_search") GoogleSearch googleSearch,
            @JsonProperty("google_search_retrieval") GoogleSearchRetrieval googleSearchRetrieval) {

        public static Tool ofFunctionDeclarations(List<FunctionDeclaration> functionDeclarations) {
            return new Tool(functionDeclarations, null, null);
        }

        public static Tool ofGoogleSearch() {
            return new Tool(null, new GoogleSearch(), null);
        }

        public static Tool ofGoogleSearchRetrieval() {
            return new Tool(null, null, new GoogleSearchRetrieval());
        }

        public record GoogleSearch() {
        }

        public record GoogleSearchRetrieval() {
        }
    }

    public record ToolConfig(FunctionCallingConfig functionCallingConfig) {

        public record FunctionCallingConfig(Mode mode) {

        }

        public enum Mode {
            AUTO,
            ANY,
            NONE
        }
    }

    /**
     * Model Armor templates that Vertex AI uses to screen the prompt and the response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelArmorConfig(String promptTemplateName, String responseTemplateName) {

    }
}
