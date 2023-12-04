package io.quarkiverse.langchain4j.deployment.devui;

import java.util.List;

public class AiServiceInfo {

    private String clazz;
    private List<String> tools;

    public AiServiceInfo(String clazz, List<String> tools) {
        this.clazz = clazz;
        this.tools = tools;
    }

    public List<String> getTools() {
        return tools;
    }

    public String getClazz() {
        return clazz;
    }

}
