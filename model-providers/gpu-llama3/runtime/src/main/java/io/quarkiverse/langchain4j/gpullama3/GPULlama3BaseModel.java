package io.quarkiverse.langchain4j.gpullama3;

import java.util.List;
import java.util.function.Consumer;

import org.beehive.jitllm.api.GenerationEvent;
import org.beehive.jitllm.api.GenerationRequest;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.LocalModel;
import org.beehive.jitllm.api.ModelCapabilities;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

/**
 * Shared generation for the chat and streaming beans.
 *
 * <p>
 * Migrated to the engine's public façade (GPULlama3 T12.9b). It used to assemble the Llama 3.2
 * chat template by hand — beginning-of-text, tool definitions into the first user message, an
 * environment prefix on the system turn, the thinking-control primer, tool-aware stop tokens — all
 * of which is the engine's now. This class translates vocabulary and nothing else.
 *
 * <p>
 * It does not own the model or the session: {@link GPULlama3ModelHolder} does, and closes them
 * once at application shutdown.
 */
abstract class GPULlama3BaseModel {

    private static final Logger LOG = Logger.getLogger(GPULlama3BaseModel.class);

    /**
     * Centralized holder of the loaded model and the session both beans generate through.
     * *Shared* across ChatModel and StreamingChatModel instances. Lazily initialized by
     * ensureInitialized() when first used.
     */
    GPULlama3ModelHolder holder;

    public LocalModel getModel() {
        return holder.model;
    }

    /**
     * Runs inference for the given {@link ChatRequest}.
     *
     * @param onEvent receives one ordered event per emitted completion token — its id and the text
     *        it completed — or {@code null} for a non-streaming call
     */
    public GenerationResult modelResponse(ChatRequest request, Consumer<GenerationEvent> onEvent) {
        holder.ensureInitialized();

        List<ToolSpecification> tools = request.toolSpecifications();
        requireToolCalling(holder.capabilities(), holder.modelName(), tools);

        // Sampling settings from the request win over the configured ones, which win over the
        // per-family defaults the holder resolved. Leaving them unset handed every request the
        // engine's own defaults, so the configured temperature, top-p and seed had no effect.
        ChatRequestParameters parameters = request.parameters();
        GenerationRequest.Builder builder = GenerationRequest.builder()
                .messages(GPULlama3Conversions.toEngineMessages(request.messages()))
                .maxNewTokens(orDefault(parameters.maxOutputTokens(), holder.maxTokens))
                .temperature(orDefault(parameters.temperature(), holder.temperature).floatValue())
                .topP(orDefault(parameters.topP(), holder.topP).floatValue())
                .seed(holder.seed);
        if (tools != null && !tools.isEmpty()) {
            builder.tools(GPULlama3Conversions.toEngineTools(tools));
            if (LOG.isInfoEnabled()) {
                LOG.infof("[Tool turn] %d tool(s) available: %s", tools.size(),
                        tools.stream().map(ToolSpecification::name).toList());
            }
        }
        if (onEvent != null) {
            builder.onEvent(onEvent);
        }

        // The session is not thread-safe: two requests generating into it at once would
        // interleave one sequence. Both beans share it, so requests take turns.
        GenerationResult result;
        synchronized (holder.session) {
            result = holder.session.generate(builder.build());
        }
        LOG.debugf("finishReason=%s generatedTokens=%d toolCalls=%d raw response: >>>%s<<<",
                result.finishReason(), result.generatedTokens(), result.toolCalls().size(),
                result.text());
        return result;
    }

    /**
     * Refuses a request that carries tools when the loaded model's chat format cannot call them.
     *
     * <p>
     * The engine would refuse it too, but only once the conversation is being encoded, as an
     * {@link UnsupportedOperationException} that the chat bean then wraps in a generic
     * "failed to generate" error. Checking the capability the engine reports for the loaded model
     * fails before the session is borrowed, with LangChain4j's own exception for an unsupported
     * feature, naming the configured model and the tools that were offered.
     *
     * @throws UnsupportedFeatureException when {@code tools} is non-empty and the model reports no
     *         tool calling
     */
    static void requireToolCalling(ModelCapabilities capabilities, String modelName,
            List<ToolSpecification> tools) {
        if (tools == null || tools.isEmpty() || capabilities.toolCalling()) {
            return;
        }
        throw new UnsupportedFeatureException(String.format(
                "Tool calling is not supported by the model '%s': its chat format has no tool-calling"
                        + " template, so the %d tool(s) offered (%s) cannot be passed to it. Use a"
                        + " tool-capable model or send the request without tools.",
                modelName, tools.size(), tools.stream().map(ToolSpecification::name).toList()));
    }

    private static <T> T orDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }

    protected static String generateCallId() {
        return GPULlama3Conversions.generateCallId();
    }

    protected static String normalizeJson(String json) {
        return GPULlama3Conversions.normalizeJson(json);
    }
}
