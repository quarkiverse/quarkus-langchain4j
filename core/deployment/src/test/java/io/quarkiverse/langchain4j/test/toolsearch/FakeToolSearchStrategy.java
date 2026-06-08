package io.quarkiverse.langchain4j.test.toolsearch;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

/**
 * Deterministic {@link ToolSearchStrategy} stub used to exercise the tool-search wiring without a real model or
 * embeddings. It exposes a single {@code search_tools} tool and always "finds" the {@code getBookingDetails} tool.
 */
@ApplicationScoped
public class FakeToolSearchStrategy implements ToolSearchStrategy {

    public static final String SEARCH_TOOL_NAME = "search_tools";
    public static final String FOUND_TOOL_NAME = "getBookingDetails";

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext invocationContext) {
        return List.of(ToolSpecification.builder()
                .name(SEARCH_TOOL_NAME)
                .description("Search for the tools that are relevant to the user request")
                .build());
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest toolSearchRequest) {
        return new ToolSearchResult(List.of(FOUND_TOOL_NAME), "found " + FOUND_TOOL_NAME);
    }
}
