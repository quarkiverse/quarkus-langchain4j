package io.quarkiverse.langchain4j.anthropic.runtime.config;

import java.util.Arrays;

/**
 * Anthropic tool search type.
 * <p>
 * For more details, see the <a href=
 * "https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-search-tool">Anthropic
 * documentation</a>.
 */
public enum ToolSearchType {
    BM25("tool_search_tool_bm25", "tool_search_tool_bm25_20251119"),
    REGEX("tool_search_tool_regex", "tool_search_tool_regex_20251119");

    private final String toolName;
    private final String toolType;

    ToolSearchType(String toolName, String toolType) {
        this.toolName = toolName;
        this.toolType = toolType;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolType() {
        return toolType;
    }

    public static ToolSearchType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown tool search type: " + value));
    }
}