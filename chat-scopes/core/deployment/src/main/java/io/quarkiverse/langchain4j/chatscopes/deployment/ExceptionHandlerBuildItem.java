package io.quarkiverse.langchain4j.chatscopes.deployment;

import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;

public final class ExceptionHandlerBuildItem extends MultiBuildItem {
    private final List<String> routes;
    private final String className;
    private final String methodName;

    public ExceptionHandlerBuildItem(List<String> routes, String className, String methodName) {
        this.routes = routes;
        this.className = className;
        this.methodName = methodName;
    }

    public List<String> getRoutes() {
        return routes;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }
}
