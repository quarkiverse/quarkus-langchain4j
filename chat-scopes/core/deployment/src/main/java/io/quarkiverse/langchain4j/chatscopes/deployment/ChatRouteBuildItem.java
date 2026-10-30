package io.quarkiverse.langchain4j.chatscopes.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class ChatRouteBuildItem extends MultiBuildItem {

    private final String routeName;
    protected String className;
    private final String methodName;
    private final boolean defaultRoute;
    private final boolean streaming;

    public ChatRouteBuildItem(String routeName, String className, String methodName, boolean defaultRoute,
            boolean streaming) {
        this.routeName = routeName;
        this.className = className;
        this.methodName = methodName;
        this.defaultRoute = defaultRoute;
        this.streaming = streaming;
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

    /**
     * @return true when the route returns a {@code Multi<String>}, meaning the client receives the answer
     *         through the stream handler rather than the message handler
     */
    public boolean isStreaming() {
        return streaming;
    }
}
