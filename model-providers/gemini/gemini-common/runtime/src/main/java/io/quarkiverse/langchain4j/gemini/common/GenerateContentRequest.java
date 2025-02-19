package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

public record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
        GenerationConfig generationConfig) {

    public record Content(String role, List<Part> parts) {

        public record Part(String text, FunctionCall functionCall, FunctionResponse functionResponse) {

            public static Part ofText(String text) {
                return new Part(text, null, null);
            }

            public static Part ofFunctionCall(FunctionCall functionCall) {
                return new Part(null, functionCall, null);
            }

            public static Part ofFunctionResponse(FunctionResponse functionResponse) {
                return new Part(null, null, functionResponse);
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
