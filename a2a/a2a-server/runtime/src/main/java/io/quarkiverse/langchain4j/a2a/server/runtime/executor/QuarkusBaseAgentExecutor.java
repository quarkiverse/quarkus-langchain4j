package io.quarkiverse.langchain4j.a2a.server.runtime.executor;

import java.util.List;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;

/**
 * This class forms the base of the {@link AgentExecutor} class that Quarkus generates in order to support
 * automatic exposure of an AI service as an A2A server
 */
@SuppressWarnings("unused") // the unused parts are used by the generated code
public abstract class QuarkusBaseAgentExecutor implements AgentExecutor {

    @Override
    public void execute(final RequestContext context,
            final EventQueue eventQueue) throws JSONRPCError {
        final TaskUpdater updater = new TaskUpdater(context, eventQueue);

        // mark the task as submitted and start working on it
        if (context.getTask() == null) {
            updater.submit();
        }
        updater.startWork();

        List<Part<?>> invocationResult = invoke(context.getMessage());

        // add the response as an artifact and complete the task
        updater.addArtifact(invocationResult, null, null, null);
        updater.complete();
    }

    /**
     * This part is generated at build time to match the AI Service being exposed
     */
    protected abstract List<Part<?>> invoke(Message message);

    @Override
    public void cancel(final RequestContext context,
            final EventQueue eventQueue) throws JSONRPCError {
        final Task task = context.getTask();

        if (task.getStatus().state() == TaskState.CANCELED) {
            // task already cancelled
            throw new TaskNotCancelableError();
        }

        if (task.getStatus().state() == TaskState.COMPLETED) {
            // task already completed
            throw new TaskNotCancelableError();
        }

        // cancel the task
        final TaskUpdater updater = new TaskUpdater(context, eventQueue);
        updater.cancel();
    }

    protected String textPartsToString(final Message message) {
        final StringBuilder textBuilder = new StringBuilder();
        if (message.getParts() != null) {
            for (final Part<?> part : message.getParts()) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.getText());
                }
            }
        }
        return textBuilder.toString();
    }

    protected List<Part<?>> stringResultToParts(String result) {
        return List.of(new TextPart(result, null));
    }

}
