package io.quarkiverse.langchain4j.chatscopes.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class ChatRouteBuildItem extends MultiBuildItem {

    private final String frameName;
    protected String className;
    private final String methodName;
    private final boolean defaultFrame;

    public ChatRouteBuildItem(String frameName, String className, String methodName, boolean defaultFrame) {
        this.frameName = frameName;
        this.className = className;
        this.methodName = methodName;
        this.defaultFrame = defaultFrame;
    }

    public String getFrameName() {
        return frameName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isDefaultFrame() {
        return defaultFrame;
    }
}
