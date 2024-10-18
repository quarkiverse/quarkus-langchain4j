package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;

public record TextRerankRequest(String modelId, String projectId, String query, List<TextRerankInput> inputs,
        TextRerankParameters parameters) {

    public static TextRerankRequest of(String modelId, String projectId, String query, List<TextSegment> segments,
            TextRerankParameters parameters) {
        var inputs = segments.stream().map(TextRerankInput::of).toList();
        return new TextRerankRequest(modelId, projectId, query, inputs, parameters);
    }

    public record TextRerankInput(String text) {
        public static TextRerankInput of(TextSegment textSegment) {
            return new TextRerankInput(textSegment.text());
        }
    };

}
