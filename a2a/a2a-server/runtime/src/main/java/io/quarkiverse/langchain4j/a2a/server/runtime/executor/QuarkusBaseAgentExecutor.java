package io.quarkiverse.langchain4j.a2a.server.runtime.executor;

import java.util.List;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.jboss.logging.Logger;

/**
 * This class forms the base of the {@link AgentExecutor} class that Quarkus generates in order to support
 * automatic exposure of an AI service as an A2A server.
 * <p>
 * The AI Service is invoked once and its result emitted as a single artifact, so a task never reaches the
 * {@code INPUT_REQUIRED} or {@code AUTH_REQUIRED} states. An AI Service that throws leaves the task
 * {@code FAILED}, which is the state the specification defines for work that could not be completed.
 *
 * @see <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">A2A spec §4.1.3, TaskState</a>
 */
@SuppressWarnings("unused")
public abstract class QuarkusBaseAgentExecutor implements AgentExecutor {

    private static final Logger LOG = Logger.getLogger(QuarkusBaseAgentExecutor.class);

    /**
     * An artifact cannot carry zero parts, so an empty result fails the task here rather than raising an SDK
     * invariant that would reach the client as an internal error.
     */
    @Override
    public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
        String taskId = context.getTaskId();
        if (context.getTask() == null) {
            emitter.submit();
        }
        emitter.startWork();

        List<Part<?>> invocationResult;
        try {
            invocationResult = invoke(context);
        } catch (A2AError e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.error("The exposed AI Service threw while working on task " + taskId, e);
            emitter.fail(failureMessage(emitter, "The agent failed to answer."));
            return;
        }

        if (invocationResult.isEmpty()) {
            LOG.error("The exposed AI Service returned no result for task " + taskId);
            emitter.fail(failureMessage(emitter, "The agent produced no answer."));
            return;
        }

        emitter.addArtifact(invocationResult);
        emitter.complete();
    }

    /**
     * Describes the failure to the client, on the task rather than as a transport error, which is the state
     * {@code FAILED} the specification defines for work that could not be completed.
     * <p>
     * The text never carries the exception. Whoever can reach the endpoint receives it, and what a model provider
     * or a tool puts in an exception message, such as internal addresses, request bodies or fragments of
     * credentials, is not theirs to read. The server log holds the exception under the same task id.
     */
    private static Message failureMessage(AgentEmitter emitter, String text) {
        Message.Builder builder = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(new TextPart(text, null));
        if (emitter.getTaskId() != null) {
            builder.taskId(emitter.getTaskId());
        }
        if (emitter.getContextId() != null) {
            builder.contextId(emitter.getContextId());
        }
        return builder.build();
    }

    /**
     * This part is generated at build time to match the AI Service being exposed
     */
    protected abstract List<Part<?>> invoke(RequestContext context);

    @Override
    public void cancel(final RequestContext context, final AgentEmitter emitter) throws A2AError {
        final Task task = context.getTask();

        if (task.status().state().isFinal()) {
            throw new TaskNotCancelableError();
        }

        emitter.cancel();
    }

    protected List<Part<?>> stringResultToParts(String result) {
        if (result == null) {
            return List.of();
        }
        return List.of(new TextPart(result, null));
    }

}
