package io.quarkiverse.langchain4j.a2a.server.runtime.executor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class forms the base of the {@link AgentExecutor} class that Quarkus generates in order to support
 * automatic exposure of an AI service as an A2A server.
 * <p>
 * The AI Service is invoked once and its result emitted as a single artifact, so a task never reaches the
 * {@code INPUT_REQUIRED} or {@code AUTH_REQUIRED} states. An AI Service that throws leaves the task
 * {@code FAILED}, which is the state the specification defines for work that could not be completed.
 *
 * @see <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">A2A spec §4.1.3 — TaskState</a>
 */
@SuppressWarnings("unused") // the unused parts are used by the generated code
public abstract class QuarkusBaseAgentExecutor implements AgentExecutor {

    private static final Logger LOG = Logger.getLogger(QuarkusBaseAgentExecutor.class);

    /**
     * The application's own {@link ObjectMapper}, which is the one that knows how to encode the types it declares.
     */
    @Inject
    ObjectMapper objectMapper;

    /**
     * Tasks cancelled while the AI Service was still working on them. Cancelling does not stop that call, so its
     * answer still arrives, and it has to be dropped rather than attached to a task the client was told is
     * cancelled.
     */
    private final Set<String> cancelledTasks = ConcurrentHashMap.newKeySet();

    @Override
    public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
        // mark the task as submitted and start working on it
        if (context.getTask() == null) {
            emitter.submit();
        }
        emitter.startWork();

        List<Part<?>> invocationResult;
        try {
            invocationResult = invoke(context);
        } catch (A2AError e) {
            // the AI Service, or the code around it, already chose how the protocol should report this
            throw e;
        } catch (RuntimeException e) {
            if (cancelledTasks.remove(emitter.getTaskId())) {
                return;
            }
            LOG.error("The exposed AI Service threw while working on task " + emitter.getTaskId(), e);
            emitter.fail(failureMessage(emitter, "The agent failed to answer."));
            return;
        }

        if (cancelledTasks.remove(emitter.getTaskId())) {
            LOG.debug("Dropping the answer for task " + emitter.getTaskId() + ", which was cancelled meanwhile");
            return;
        }

        if (invocationResult.isEmpty()) {
            // an Artifact cannot carry zero parts, so completing the task here would raise an SDK invariant and
            // reach the client as an internal error rather than as the agent having produced no answer
            LOG.error("The exposed AI Service returned no result for task " + emitter.getTaskId());
            emitter.fail(failureMessage(emitter, "The agent produced no answer."));
            return;
        }

        // add the response as an artifact and complete the task
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

        // COMPLETED, CANCELED, FAILED and REJECTED are terminal, and a terminal task cannot be cancelled
        if (task.status().state().isFinal()) {
            throw new TaskNotCancelableError();
        }

        cancelledTasks.add(task.id());
        emitter.cancel();
    }

    protected List<Part<?>> stringResultToParts(String result) {
        if (result == null) {
            return List.of();
        }
        return List.of(new TextPart(result, null));
    }

    /**
     * Used for AI Services that return anything other than a {@code String}. Such a result is structured data
     * rather than prose, so it travels as a {@link DataPart} — matching the {@code application/json} output mode
     * the Agent Card advertises for these signatures.
     */
    protected List<Part<?>> objectResultToParts(Object result) {
        if (result == null) {
            return List.of();
        }
        try {
            return List.of(DataPart.fromJson(objectMapper.writeValueAsString(result)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Unable to serialize the result of type '" + result.getClass().getName() + "' into an A2A part", e);
        }
    }

}
