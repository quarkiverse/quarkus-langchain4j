package io.quarkiverse.langchain4j.deployment.devui;

public class ToolMethodInfo {

    private String className;

    private String name;

    private String description;

    public ToolMethodInfo(String className, String name, String description) {
        this.className = className;
        this.name = name;
        this.description = description;
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
