package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext.ChatScopeImpl;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryRemovable;
import io.quarkus.arc.ContextInstanceHandle;

public class ChatScopeMemoryImpl {
    static Logger log = Logger.getLogger(ChatScopeMemoryImpl.class);

    static ChatScopeImpl current() {
        return (ChatScopeImpl) ChatScope.current();
    }

    public static void clearMemory() {
        if (!ChatScope.isActive()) {
            return;
        }
        ChatScopeImpl scope = current();
        clearMemory(scope);
    }

    public static void clearMemory(ChatScopeImpl scope) {
        List<ContextInstanceHandle<ChatMemoryRemovable>> beans = scope.getBeans(ChatMemoryRemovable.class);
        log.debugv("chat memory removable Beans: {0}", beans.size());
        for (ContextInstanceHandle<ChatMemoryRemovable> bean : beans) {
            bean.get().removeAll();
        }

    }

    private enum WipeState {
        SCHEDULED,
        ABORTED,
    }

    public static void clearWipeState(ChatScopeImpl scope) {
        scope.scopeState().remove(WipeState.ABORTED);
        scope.scopeState().remove(WipeState.SCHEDULED);
    }

    public static void scheduleWipe() {
        ChatScopeImpl scope = current();
        if (Boolean.TRUE.equals(scope.scopeState().get(WipeState.ABORTED))) {
            return;
        }
        scope.scopeState().put(WipeState.SCHEDULED, true);
    }

    public static void abortWipe() {
        ChatScopeImpl scope = current();
        scope.scopeState().put(WipeState.ABORTED, true);
        scope.scopeState().remove(WipeState.SCHEDULED);
    }

    public static void executeScheduledWipes() {
        if (!ChatScope.isActive()) {
            return;
        }
        ChatScopeImpl scope = current();
        while (scope != null) {
            Boolean aborted = (Boolean) scope.scopeState().get(WipeState.ABORTED);
            Boolean scheduled = (Boolean) scope.scopeState().get(WipeState.SCHEDULED);
            boolean doWipe = Boolean.TRUE.equals(scheduled) && !Boolean.TRUE.equals(aborted);
            if (doWipe) {
                clearMemory(scope);
            }
            if (aborted != null || scheduled != null) {
                clearWipeState(scope);
            }
            scope = scope.parent;
        }
    }

    public static boolean wipeScheduled() {
        if (!ChatScope.isActive()) {
            return false;
        }
        ChatScopeImpl scope = current();
        if (Boolean.TRUE.equals(scope.scopeState().get(WipeState.ABORTED))) {
            return false;
        }
        return Boolean.TRUE.equals(scope.scopeState().get(WipeState.SCHEDULED));
    }
}
