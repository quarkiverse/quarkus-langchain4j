package io.quarkiverse.langchain4j.bam;

public record ModerationRequest(String input, Threshold hap, Threshold socialBias) {

    public static ModerationRequest of(String input, Float hap, Float socialBias) {
        return new ModerationRequest(
                input,
                hap != null ? new Threshold(hap) : null,
                socialBias != null ? new Threshold(socialBias) : null);
    }

    public record Threshold(float threshold) {
    };
}
