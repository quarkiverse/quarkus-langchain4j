package io.quarkiverse.langchain4j.test.toolresolution;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;

@ApplicationScoped
public class ToolsClass {

    @Tool
    public String hello() {
        return "EXPLICIT TOOL";
    }
}
