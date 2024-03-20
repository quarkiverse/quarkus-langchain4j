package io.quarkiverse.langchain4j.runtime.devui.json;

public class ToolExecutionResultPojo {

    private final String id;
    private final String toolName;
    private final String text;

    public ToolExecutionResultPojo(String id, String toolName, String text) {
        this.id = id;
        this.toolName = toolName;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }

    public String getText() {
        return text;
    }
}
