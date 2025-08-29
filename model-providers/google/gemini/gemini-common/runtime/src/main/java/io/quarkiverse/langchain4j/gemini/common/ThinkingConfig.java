package io.quarkiverse.langchain4j.gemini.common;

public class ThinkingConfig {

    private final Long thinkingBudget;
    private final Boolean includeThoughts;

    public ThinkingConfig(Long thinkingBudget, Boolean includeThoughts) {
        this.thinkingBudget = thinkingBudget;
        this.includeThoughts = includeThoughts;
    }

    public Long getThinkingBudget() {
        return thinkingBudget;
    }

    public Boolean getIncludeThoughts() {
        return includeThoughts;
    }
}
