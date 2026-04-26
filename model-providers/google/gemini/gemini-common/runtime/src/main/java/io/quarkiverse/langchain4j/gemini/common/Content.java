package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

public record Content(String role, List<Part> parts) {

    public static Content ofPart(Part part) {
        return new Content(null, List.of(part));
    }

    public record Part(String text, FunctionCall functionCall, FunctionResponse functionResponse, FileData fileData,
            Blob inlineData, String thoughtSignature) {

        public static Content.Part ofText(String text) {
            return new Content.Part(text, null, null, null, null, null);
        }

        public static Content.Part ofFunctionCall(FunctionCall functionCall, String thoughtSignature) {
            return new Content.Part(null, functionCall, null, null, null, thoughtSignature);
        }

        public static Content.Part ofFunctionResponse(FunctionResponse functionResponse) {
            return new Content.Part(null, null, functionResponse, null, null, null);
        }

        public static Content.Part ofFileData(FileData fileData) {
            return new Content.Part(null, null, null, fileData, null, null);
        }

        public static Content.Part ofInlineData(Blob inlineData) {
            return new Content.Part(null, null, null, null, inlineData, null);
        }
    }
}
