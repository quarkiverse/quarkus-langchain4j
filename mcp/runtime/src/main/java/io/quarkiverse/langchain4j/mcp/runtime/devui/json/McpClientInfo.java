package io.quarkiverse.langchain4j.mcp.runtime.devui.json;

import java.util.List;

public class McpClientInfo {

    private String cdiName;
    private List<McpToolInfo> tools;

    public String getCdiName() {
        return cdiName;
    }

    public void setCdiName(String cdiName) {
        this.cdiName = cdiName;
    }

    public List<McpToolInfo> getTools() {
        return tools;
    }

    public void setTools(List<McpToolInfo> tools) {
        this.tools = tools;
    }
}
