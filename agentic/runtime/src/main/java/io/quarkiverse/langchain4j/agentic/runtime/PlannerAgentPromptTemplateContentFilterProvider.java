package io.quarkiverse.langchain4j.agentic.runtime;

import static io.quarkiverse.langchain4j.agentic.runtime.Constants.PLANNER_AGENT_CONTENT_REPLACEMENT_IN_QUTE;
import static io.quarkiverse.langchain4j.agentic.runtime.Constants.PLANNER_AGENT_CONTENT_TO_REPLACE_IN_QUTE;

import java.util.function.Function;

import io.quarkiverse.langchain4j.spi.PromptTemplateFactoryContentFilterProvider;

public class PlannerAgentPromptTemplateContentFilterProvider implements PromptTemplateFactoryContentFilterProvider {
    @Override
    public Function<String, String> getContentFilter() {
        return new Function<>() {
            @Override
            public String apply(String s) {
                return s.replace(PLANNER_AGENT_CONTENT_TO_REPLACE_IN_QUTE, PLANNER_AGENT_CONTENT_REPLACEMENT_IN_QUTE);
            }
        };
    }
}
