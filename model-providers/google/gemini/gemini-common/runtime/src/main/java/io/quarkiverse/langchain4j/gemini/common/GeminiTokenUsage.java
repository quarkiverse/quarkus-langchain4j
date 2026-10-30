package io.quarkiverse.langchain4j.gemini.common;

import java.util.Objects;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Token usage of a Gemini response. As in LangChain4j's Google AI Gemini module, the output token count excludes the
 * thinking tokens, which are reported separately.
 */
public class GeminiTokenUsage extends TokenUsage {

    private final Integer cachedContentTokenCount;
    private final Integer thoughtsTokenCount;

    public GeminiTokenUsage(Integer inputTokenCount, Integer outputTokenCount, Integer totalTokenCount,
            Integer cachedContentTokenCount, Integer thoughtsTokenCount) {
        super(inputTokenCount, outputTokenCount, totalTokenCount);
        this.cachedContentTokenCount = cachedContentTokenCount;
        this.thoughtsTokenCount = thoughtsTokenCount;
    }

    public Integer cachedContentTokenCount() {
        return cachedContentTokenCount;
    }

    public Integer thoughtsTokenCount() {
        return thoughtsTokenCount;
    }

    @Override
    public GeminiTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }
        Integer thatCachedContentTokenCount = null;
        Integer thatThoughtsTokenCount = null;
        if (that instanceof GeminiTokenUsage thatGeminiTokenUsage) {
            thatCachedContentTokenCount = thatGeminiTokenUsage.cachedContentTokenCount;
            thatThoughtsTokenCount = thatGeminiTokenUsage.thoughtsTokenCount;
        }
        return new GeminiTokenUsage(
                sum(inputTokenCount(), that.inputTokenCount()),
                sum(outputTokenCount(), that.outputTokenCount()),
                sum(totalTokenCount(), that.totalTokenCount()),
                sum(cachedContentTokenCount, thatCachedContentTokenCount),
                sum(thoughtsTokenCount, thatThoughtsTokenCount));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        GeminiTokenUsage that = (GeminiTokenUsage) o;
        return Objects.equals(cachedContentTokenCount, that.cachedContentTokenCount)
                && Objects.equals(thoughtsTokenCount, that.thoughtsTokenCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cachedContentTokenCount, thoughtsTokenCount);
    }

    @Override
    public String toString() {
        return "GeminiTokenUsage {"
                + " inputTokenCount = " + inputTokenCount()
                + ", outputTokenCount = " + outputTokenCount()
                + ", totalTokenCount = " + totalTokenCount()
                + ", cachedContentTokenCount = " + cachedContentTokenCount
                + ", thoughtsTokenCount = " + thoughtsTokenCount
                + " }";
    }
}
