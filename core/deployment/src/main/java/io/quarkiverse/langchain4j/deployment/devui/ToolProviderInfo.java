package io.quarkiverse.langchain4j.deployment.devui;

public class ToolProviderInfo {
    private String className;
    private String aiServiceName;

    public ToolProviderInfo(String className, String aiServiceName) {
        this.className = className;
        this.aiServiceName = aiServiceName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getAiServiceName() {
        return aiServiceName;
    }

    public void setAiServiceName(String aiServiceName) {
        this.aiServiceName = aiServiceName;
    }
}
