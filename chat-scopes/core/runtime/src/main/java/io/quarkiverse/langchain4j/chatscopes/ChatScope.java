package io.quarkiverse.langchain4j.chatscopes;

import jakarta.enterprise.context.ContextNotActiveException;

import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;

public interface ChatScope {
    public static final String THREAD_CONTEXT_TYPE = "CHAT_SCOPE";

    String getId();

    String getRoute();

    void setRoute(String route);

    /**
     * Check if the current thread has a chat scope active.
     *
     * @return
     */
    static boolean isActive() {
        return ChatScopeManagedContext.INSTANCE.currentContext() != null;
    }

    static ChatScope activate(String id) {
        return ChatScopeManagedContext.INSTANCE.activate(id);
    }

    static void deactivate() {
        ChatScopeManagedContext.INSTANCE.deactivate();
    }

    /**
     * Get the current chat scope.
     *
     * @return
     */
    static ChatScope current() {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
        return ChatScopeManagedContext.INSTANCE.currentContext();
    }

    /**
     * Get the current chat scope id.
     *
     * @return
     */
    static String id() {
        return current().getId();
    }

    /**
     * Create a new nested chat scope conversation and push it onto the chat scope stack.
     *
     * @return
     */
    static ChatScope push() {
        return ChatScopeManagedContext.INSTANCE.push();
    }

    /**
     * Push a new chat scope onto the stack and also set the current route too.
     * Shorthand for {@code ChatScope.push(); ChatRoutes.current(route);}
     *
     * @param route
     * @return
     */
    static ChatScope push(String route) {
        return ChatScopeManagedContext.INSTANCE.push(route);
    }

    /**
     * Pop the current chat scope off the stack. This will also destroy the current chat scope,
     * all the CDI chat scoped beans of that scope
     */
    static void pop() {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
        ChatScopeManagedContext.INSTANCE.pop();
    }

    /**
     * Begin a new chat scope conversation.
     *
     * @return
     */
    static ChatScope begin() {
        return ChatScopeManagedContext.INSTANCE.begin();
    }

    /**
     * Begin a new chat scope conversation and set the current route too.
     *
     * @param route
     * @return
     */
    static ChatScope begin(String route) {
        return ChatScopeManagedContext.INSTANCE.begin(route);
    }

    /**
     * End the current chat scope conversation.
     */
    static void end() {
        if (!isActive()) {
            throw new ContextNotActiveException();
        }
        ChatScopeManagedContext.INSTANCE.end();
    }
}
