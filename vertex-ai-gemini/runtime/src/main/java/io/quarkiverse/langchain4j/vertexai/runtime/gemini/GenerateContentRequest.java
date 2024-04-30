package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;

public record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
        GenerationConfig generationConfig) {

    public record Content(String role, List<Part> parts) {

        public record Part(String text, FunctionCall functionCall) {

            public static Part ofText(String text) {
                return new Part(text, null);
            }
        }
    }

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
