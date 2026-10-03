package io.quarkiverse.langchain4j.a2a.server.runtime.authorization;

import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.jboss.logging.Logger;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.lookup.LookupUnlessProperty;

/**
 * Denies every task operation, the first message included, so that an agent never serves one caller's tasks to
 * another unless the application says who owns them. A {@link TaskAuthorizationProvider} bean of the application's
 * own replaces it.
 * <p>
 * The SDK consults a provider whenever there is one and only reads {@code a2a.authorization.required} when there is
 * none, so this provider steps aside when that property is {@code false}.
 */
@DefaultBean
@Singleton
@LookupUnlessProperty(name = DenyingTaskAuthorizationProvider.AUTHORIZATION_REQUIRED, stringValue = "false", lookupIfMissing = true)
public class DenyingTaskAuthorizationProvider implements TaskAuthorizationProvider {

    public static final String AUTHORIZATION_REQUIRED = "a2a.authorization.required";

    /**
     * The SDK reports a denial as {@code TaskNotFoundError}, so that a caller cannot tell a task it may not see from
     * one that does not exist, which also hides from the developer why every request fails.
     */
    public static final String EXPLANATION = "Every A2A task operation is denied, the first message included, and"
            + " reported to the client as TaskNotFoundError, because the application supplies no"
            + " TaskAuthorizationProvider. Add a TaskAuthorizationProvider bean that says who owns each task, or set "
            + AUTHORIZATION_REQUIRED + "=false if every caller may see every task";

    private static final Logger LOG = Logger.getLogger(DenyingTaskAuthorizationProvider.class);

    @Override
    public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
        return deny(operation);
    }

    @Override
    public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
        return deny(operation);
    }

    @Override
    public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
        return deny(operation);
    }

    @Override
    public boolean isTaskRecorded(String taskId) {
        return false;
    }

    @Override
    public void recordOwnership(ServerCallContext context, String taskId, TaskOperation operation) {
    }

    private static boolean deny(TaskOperation operation) {
        LOG.debugf("Denied %s: %s", operation, EXPLANATION);
        return false;
    }
}
