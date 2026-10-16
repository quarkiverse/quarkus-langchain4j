package io.quarkiverse.langchain4j.gemini.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThinkingConfig {

    private final Long thinkingBudget;
    private final Boolean includeThoughts;
    private final ThinkingLevel thinkingLevel;

    public ThinkingConfig(Long thinkingBudget, Boolean includeThoughts) {
        this(thinkingBudget, includeThoughts, null);
    }

    public ThinkingConfig(Long thinkingBudget, Boolean includeThoughts, ThinkingLevel thinkingLevel) {
        this.thinkingBudget = thinkingBudget;
        this.includeThoughts = includeThoughts;
        this.thinkingLevel = thinkingLevel;
    }

    public Long getThinkingBudget() {
        return thinkingBudget;
    }

    public Boolean getIncludeThoughts() {
        return includeThoughts;
    }

    public ThinkingLevel getThinkingLevel() {
        return thinkingLevel;
    }
}
