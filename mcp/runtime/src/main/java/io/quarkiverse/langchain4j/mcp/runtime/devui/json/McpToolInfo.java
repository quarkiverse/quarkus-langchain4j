package io.quarkiverse.langchain4j.mcp.runtime.devui.json;

import java.util.List;

public class McpToolInfo {

    private String name;
    private String description;
    private String exampleInput;
    private List<McpToolArgInfo> args;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExampleInput() {
        return exampleInput;
    }

    public void setExampleInput(String exampleInput) {
        this.exampleInput = exampleInput;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<McpToolArgInfo> getArgs() {
        return args;
    }

    public void setArgs(List<McpToolArgInfo> args) {
        this.args = args;
    }
}
