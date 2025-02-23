package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

public record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
        GenerationConfig generationConfig) {

    public record SystemInstruction(List<Part> parts) {

        public static SystemInstruction ofContent(List<String> contents) {
            return new SystemInstruction(contents.stream().map(Part::new).toList());
        }

        public record Part(String text) {

        }
    }

    public record Tool(List<FunctionDeclaration> functionDeclarations) {

    }

}
