package io.quarkiverse.langchain4j.chatscopes.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class ChatRouteBuildItem extends MultiBuildItem {

    private final String routeName;
    protected String className;
    private final String methodName;
    private final boolean defaultRoute;

    public ChatRouteBuildItem(String routeName, String className, String methodName, boolean defaultRoute) {
        this.routeName = routeName;
        this.className = className;
        this.methodName = methodName;
        this.defaultRoute = defaultRoute;
    }

    public String getRouteName() {
        return routeName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isDefaultRoute() {
        return defaultRoute;
    }
}
