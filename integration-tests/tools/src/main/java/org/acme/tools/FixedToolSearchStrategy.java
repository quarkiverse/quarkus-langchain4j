package org.acme.tools;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

/**
 * Deterministic tool search strategy: exposes a single {@code find_tools} tool and always surfaces the booking tool.
 */
@ApplicationScoped
public class FixedToolSearchStrategy implements ToolSearchStrategy {

    public static final String SEARCH_TOOL_NAME = "find_tools";
    public static final String FOUND_TOOL_NAME = "getBookingDetails";

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext invocationContext) {
        return List.of(ToolSpecification.builder()
                .name(SEARCH_TOOL_NAME)
                .description("Search for the tools relevant to the user request")
                .build());
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest toolSearchRequest) {
        return new ToolSearchResult(List.of(FOUND_TOOL_NAME), "found " + FOUND_TOOL_NAME);
    }
}
