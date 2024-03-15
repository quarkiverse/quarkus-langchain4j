package io.quarkiverse.langchain4j.bam;

public record ModerationRequest(String input, Threshold implicitHate, Threshold hap, Threshold stigma) {

    public static ModerationRequest of(String input, Float implicitHate, Float hap, Float stigma) {
        return new ModerationRequest(
                input,
                implicitHate != null ? new Threshold(implicitHate) : null,
                hap != null ? new Threshold(hap) : null,
                stigma != null ? new Threshold(stigma) : null);
    }

    public record Threshold(float threshold) {
    };
}
