package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;

public record ScoringRequest(String modelId, String projectId, String query, List<ScoringInput> inputs,
        ScoringParameters parameters) {

    public static ScoringRequest of(String modelId, String projectId, String query, List<TextSegment> segments,
            ScoringParameters parameters) {
        var inputs = segments.stream().map(ScoringInput::of).toList();
        return new ScoringRequest(modelId, projectId, query, inputs, parameters);
    }

    public record ScoringInput(String text) {
        public static ScoringInput of(TextSegment textSegment) {
            return new ScoringInput(textSegment.text());
        }
    };

}
