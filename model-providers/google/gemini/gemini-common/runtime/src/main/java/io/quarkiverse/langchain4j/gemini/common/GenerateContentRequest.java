package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

public record GenerateContentRequest(List<Content> contents, SystemInstruction systemInstruction, List<Tool> tools,
        GenerationConfig generationConfig) {

    public record Content(String role, List<Part> parts) {

        public record Part(String text, FunctionCall functionCall, FunctionResponse functionResponse, FileData fileData,
                Blob inlineData) {

            public static Part ofText(String text) {
                return new Part(text, null, null, null, null);
            }

            public static Part ofFunctionCall(FunctionCall functionCall) {
                return new Part(null, functionCall, null, null, null);
            }

            public static Part ofFunctionResponse(FunctionResponse functionResponse) {
                return new Part(null, null, functionResponse, null, null);
            }

            public static Part ofFileData(FileData fileData) {
                return new Part(null, null, null, fileData, null);
            }

            public static Part ofInlineData(Blob inlineData) {
                return new Part(null, null, null, null, inlineData);
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
