package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
        GenerationConfig generationConfig) {

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

}
