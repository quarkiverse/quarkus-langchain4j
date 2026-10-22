package io.quarkiverse.langchain4j.a2a.server.runtime.executor;

import java.util.List;
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
 * @see <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">A2A spec §4.1.3, TaskState</a>
 */
@SuppressWarnings("unused")
public abstract class QuarkusBaseAgentExecutor implements AgentExecutor {

    private static final Logger LOG = Logger.getLogger(QuarkusBaseAgentExecutor.class);

    /**
     * The application's own {@link ObjectMapper}, which is the one that knows how to encode the types it declares.
     */
    @Inject
    ObjectMapper objectMapper;

    /**
     * The tasks this executor is working on, each mapped to whether it was cancelled meanwhile. Cancelling does not
     * stop the call into the AI Service, so its answer still arrives, and it has to be dropped rather than attached
     * to a task the client was told is cancelled. An entry lives only while {@link #execute} runs.
     */
    private final ConcurrentHashMap<String, Boolean> runningTasks = new ConcurrentHashMap<>();

    @Override
    public void execute(final RequestContext context, final AgentEmitter emitter) throws A2AError {
        String taskId = context.getTaskId();
        runningTasks.put(taskId, Boolean.FALSE);
        try {
            work(context, emitter, taskId);
        } finally {
            runningTasks.remove(taskId);
        }
    }

    /**
     * An artifact cannot carry zero parts, so an empty result fails the task here rather than raising an SDK
     * invariant that would reach the client as an internal error.
     */
    private void work(RequestContext context, AgentEmitter emitter, String taskId) throws A2AError {
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
            if (wasCancelled(taskId)) {
                return;
            }
            LOG.error("The exposed AI Service threw while working on task " + taskId, e);
            emitter.fail(failureMessage(emitter, "The agent failed to answer."));
            return;
        }

        if (wasCancelled(taskId)) {
            LOG.debug("Dropping the answer for task " + taskId + ", which was cancelled meanwhile");
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

    private boolean wasCancelled(String taskId) {
        return Boolean.TRUE.equals(runningTasks.get(taskId));
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
     * The memory id an exposed method receives. An A2A context groups the interactions that belong together, which
     * is what a memory id is.
     */
    protected static String memoryId(RequestContext context) {
        return context.getContextId();
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

        runningTasks.replace(task.id(), Boolean.FALSE, Boolean.TRUE);
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
     * rather than prose, so it travels as a {@link DataPart}, matching the {@code application/json} output mode
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
