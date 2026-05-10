package io.quarkiverse.langchain4j;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.spi.services.AiServicesFactory;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceInvocationData;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemorySeeder;
import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusInvocationData;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusStreamingToolDispatchHook;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.WorkerSwitchingStreamingChatModel;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jConfig;
import io.smallrye.config.SmallRyeConfig;

public class QuarkusAiServicesFactory implements AiServicesFactory {

    @Override
    public <T> QuarkusAiServices<T> create(AiServiceContext context) {
        if (context instanceof QuarkusAiServiceContext) {
            return new QuarkusAiServices<>(context);
        } else {
            // the context is always empty (except for the aiServiceClass) anyway and never escapes, so we can just use our own type
            return new QuarkusAiServices<>(new QuarkusAiServiceContext(context.aiServiceClass));
        }
    }

    public static class InstanceHolder {
        public static final QuarkusAiServicesFactory INSTANCE = new QuarkusAiServicesFactory();
    }

    public static class QuarkusAiServices<T> extends AiServices<T> {

        private static final Logger LOG = Logger.getLogger(QuarkusAiServices.class);

        public QuarkusAiServices(AiServiceContext context) {
            super(context);
        }

        @Override
        public AiServices<T> tools(Collection<Object> objectsWithTools) {
            // Build AiServiceTool instances carrying the per-tool ReturnBehavior so upstream's
            // ToolService.executeInferenceAndToolsLoop can short-circuit on IMMEDIATE.
            //
            // QuarkusAiServiceContextFactory looks up the existing per-AiService CDI bean and
            // returns it for the programmatic AiServices.builder(...) path, so the underlying
            // toolService may already contain tools registered by the declarative bean
            // construction. Upstream's ToolService.tools(List<AiServiceTool>) treats already-known
            // tool names as a fatal misconfiguration; the legacy Quarkus implementation silently
            // overwrote them. Preserve the legacy semantics: skip names that are already present
            // on this context's ToolService.
            List<AiServiceTool> built = ToolsRecorder.buildAiServiceTools(objectsWithTools);
            Set<String> existing = context.toolService.toolExecutors().keySet();
            List<AiServiceTool> toAdd = new ArrayList<>(built.size());
            for (AiServiceTool tool : built) {
                if (!existing.contains(tool.name())) {
                    toAdd.add(tool);
                }
            }
            if (!toAdd.isEmpty()) {
                context.toolService.tools(toAdd);
            }
            return this;
        }

        public AiServices<T> toolHallucinationStrategy(Object toolHallucinationStrategy) {
            context.toolService.hallucinatedToolNameStrategy(
                    (Function<ToolExecutionRequest, ToolExecutionResultMessage>) toolHallucinationStrategy);
            return this;
        }

        public AiServices<T> chatMemorySeeder(ChatMemorySeeder chatMemorySeeder) {
            quarkusAiServiceContext().chatMemorySeeder = chatMemorySeeder;
            return this;
        }

        public AiServices<T> systemMessageProvider(SystemMessageProvider systemMessageProvider) {
            context.systemMessageProvider = memoryId -> systemMessageProvider.getSystemMessage(memoryId);
            return this;
        }

        public AiServices<T> imageModel(ImageModel imageModel) {
            quarkusAiServiceContext().imageModel = imageModel;
            return this;
        }

        @Override
        public AiServices<T> maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
            quarkusAiServiceContext().maxSequentialToolExecutions = maxSequentialToolsInvocations;
            return this;
        }

        public AiServices<T> maxToolCallsPerResponse(Integer maxToolCallsPerResponse) {
            quarkusAiServiceContext().maxToolCallsPerResponse = maxToolCallsPerResponse;
            return this;
        }

        public AiServices<T> allowContinuousForcedToolCalling(boolean allowContinuousForcedToolCalling) {
            quarkusAiServiceContext().allowContinuousForcedToolCalling = allowContinuousForcedToolCalling;
            return this;
        }

        public AiServices<T> chatMemoryFlushStrategy(
                ChatMemoryFlushStrategy chatMemoryFlushStrategy) {
            quarkusAiServiceContext().chatMemoryFlushStrategy = chatMemoryFlushStrategy;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T build() {
            Class<?> aiServiceClass = context.aiServiceClass;
            AiServiceClassCreateInfo classCreateInfo = AiServicesRecorder.getMetadata().get(aiServiceClass.getName());
            if (classCreateInfo == null) {
                throw new RuntimeException("Quarkus was not able to determine class '" + aiServiceClass.getName()
                        + "' as an AiService at build time. Consider annotating the class with "
                        + "'@CreatedAware'");
            }

            performBasicValidation();

            Collection<AiServiceMethodCreateInfo> methodCreateInfos = classCreateInfo.methodMap().values();
            for (var methodCreateInfo : methodCreateInfos) {
                if (methodCreateInfo.isRequiresModeration() && ((context.moderationModel == null))) {
                    throw illegalConfiguration(
                            "The @Moderate annotation is present, but the moderationModel is not set up. " +
                                    "Please ensure a valid moderationModel is configured before using the @Moderate "
                                    + "annotation.");
                }
            }

            // Resolve the parallel-tool executor for this AiService and hand it to upstream
            // ToolService via executeToolsConcurrently(...). Null = serial (default), in which case
            // upstream's tool dispatch stays sequential.
            //
            // The resolver is keyed by AiService NAME (the same key used under quarkus.langchain4j.<name>.*),
            // never the FQCN. The build-time AiServiceClassCreateInfo carries the resolved name (the @Named bean
            // value when present, otherwise the simple class name). For programmatically built AiServices that
            // somehow lack the recorded name, we fall back to the simple class name — never the FQCN.
            String aiServiceName = classCreateInfo.aiServiceName();
            if (aiServiceName == null) {
                aiServiceName = aiServiceClass.getSimpleName();
            }
            Executor parallelExecutor = null;
            try {
                LangChain4jConfig langChain4jConfig = ConfigProvider.getConfig()
                        .unwrap(SmallRyeConfig.class)
                        .getConfigMapping(LangChain4jConfig.class);
                parallelExecutor = ParallelToolExecutorResolver.resolve(aiServiceName, langChain4jConfig);
            } catch (UnsupportedOperationException e) {
                // Environment-driven fallback only: the conventional Java signal for "feature exists but is not
                // usable in this JVM/runtime" (e.g. virtual threads when running on Java 17). Log INFO and fall back
                // to serial dispatch so the application keeps working.
                //
                // All other RuntimeExceptions (IllegalArgumentException for invalid concurrency, IllegalStateException
                // for unknown modes, SmallRye config-mapping failures, etc.) propagate so misconfiguration fails
                // loudly at startup with a clear error message instead of silently degrading to serial.
                LOG.infof(e,
                        "Parallel-tool executor unavailable in this environment for AiService '%s'; falling back to serial dispatch.",
                        aiServiceName);
            }
            quarkusAiServiceContext().parallelToolExecutor = parallelExecutor;
            applyDelegationHooks(this, quarkusAiServiceContext(), parallelExecutor);

            try {
                return (T) Class.forName(classCreateInfo.implClassName(), true, Thread.currentThread()
                        .getContextClassLoader()).getConstructor(QuarkusAiServiceContext.class)
                        .newInstance(quarkusAiServiceContext());
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create class '" + classCreateInfo.implClassName(), e);
            }
        }

        private QuarkusAiServiceContext quarkusAiServiceContext() {
            return (QuarkusAiServiceContext) context;
        }

    }

    /**
     * Wires the upstream {@link AiServices} hooks the Quarkus extension uses to delegate the
     * LLM&#8596;tools loop while preserving Quarkus-specific behaviours:
     * <ul>
     * <li>{@code executeToolsConcurrently(parallelExecutor)} — when a parallel mode is enabled.
     * The executor is already wrapped in {@code VertxContextAwareExecutor}, so each task
     * re-enters the caller's Vert.x duplicated context (chat memory, request scope, OTel,
     * MDC) before running.</li>
     * <li>{@code streamingToolDispatchHook(...)} — hops the streaming tool batch dispatch off
     * the Vert.x event loop when the chat provider delivers {@code onCompleteResponse} on the
     * EL. Without this, blocking tools (Quarkus' default execution model) would trip
     * {@code BlockingToolNotAllowedException}. The hook is a no-op when the calling thread is
     * already off the EL.</li>
     * <li>{@code forceToolChoiceAutoAfterFirstIteration(...)} — preserves the Quarkus-side
     * self-protection that rewrites {@code ToolChoice.REQUIRED} to {@code AUTO} on
     * iteration 2+ unless the user opted out via {@code allowContinuousForcedToolCalling}.</li>
     * <li>{@code errorHandlerBypass(...)} — preserves the {@link PreventsErrorHandlerExecution}
     * marker semantics (e.g. {@code BlockingToolNotAllowedException}): exceptions
     * implementing the marker propagate unchanged instead of being summarized into a
     * string sent back to the LLM.</li>
     * <li>{@code toolProviderRequestFactory(...)} — every {@code ToolProviderRequest}
     * (initial creation + dynamic refresh) is built as a {@link QuarkusToolProviderRequest}
     * carrying the per-method {@code mcpClientNames}, so MCP tool providers see the same
     * shape they previously received from the now-deleted in-loop construction.</li>
     * </ul>
     * Must be called BEFORE the generated AiService impl class is instantiated.
     */
    public static void applyDelegationHooks(AiServices<?> aiServiceBuilder, QuarkusAiServiceContext context,
            Executor parallelExecutor) {
        if (parallelExecutor != null) {
            aiServiceBuilder.executeToolsConcurrently(parallelExecutor);
        }
        // Wrap context.streamingChatModel so callbacks delivered on the Vert.x event loop are
        // hopped to a worker when the caller subscribed off the EL. This preserves the
        // "blocking-friendly emission" guarantee the deleted Quarkus streaming handler had:
        // upstream's onCompleteResponse calls addToMemory synchronously on the calling thread,
        // which trips BlockingMemoryStore guards (and request-scoped CDI lookups, MDC, etc.) when
        // a chat provider delivers via Vert.x's runOnContext. Decorating once at build time means
        // both the TokenStream return type and the Multi return type benefit; the wrapper is a
        // no-op when the caller is already on the EL.
        if (context.streamingChatModel != null
                && !(context.streamingChatModel instanceof WorkerSwitchingStreamingChatModel)) {
            context.streamingChatModel = new WorkerSwitchingStreamingChatModel(context.streamingChatModel);
        }
        // Route streaming tool batch dispatch off the Vert.x event loop when the chat provider
        // delivers onCompleteResponse on the EL — otherwise blocking tools (the Quarkus default
        // execution model) trip BlockingToolNotAllowedException. The hook is a no-op when the
        // calling thread is already off the EL, so it's free for non-Vert.x callers.
        aiServiceBuilder.streamingToolDispatchHook(QuarkusStreamingToolDispatchHook.INSTANCE);
        aiServiceBuilder.forceToolChoiceAutoAfterFirstIteration(!context.allowContinuousForcedToolCalling);
        aiServiceBuilder.errorHandlerBypass(t -> t instanceof PreventsErrorHandlerExecution);
        aiServiceBuilder.toolProviderRequestFactory(builder -> {
            // mcpClientNames live on the per-method AiServiceMethodCreateInfo. The upstream ToolService
            // calls this factory both at initial context creation and during dynamic provider refresh,
            // passing a fully populated builder (invocationContext + userMessage + messages). We build
            // the upstream request, then construct a QuarkusToolProviderRequest that COPIES all three
            // fields (incl. messages) plus the per-method mcpClientNames.
            dev.langchain4j.service.tool.ToolProviderRequest req = builder.build();
            return new QuarkusToolProviderRequest(req, mcpClientNamesFor(req.invocationContext()));
        });
    }

    /**
     * Returns the per-method {@code mcpClientNames} list straight from the
     * {@link QuarkusInvocationData} that {@code AiServiceMethodImplementationSupport} stamps onto
     * the {@link dev.langchain4j.invocation.InvocationContext} via {@code managedParameters()}.
     * Nullability is meaningful: MCP's filter ({@code QuarkusMcpToolProvider.McpClientKeyFilter})
     * reads {@code keys == null} as "no MCP clients selected" (the @McpToolBox-absent default) and
     * an empty list as "select all MCP clients". Returning an empty list when the metadata is
     * absent would silently widen scope, surfacing every MCP tool to a method that previously saw
     * none — so we return {@code null} whenever the Quarkus payload is not present.
     */
    private static java.util.List<String> mcpClientNamesFor(dev.langchain4j.invocation.InvocationContext ic) {
        if (ic == null || ic.managedParameters() == null) {
            return null;
        }
        LangChain4jManaged data = ic.managedParameters().get(QuarkusInvocationData.class);
        return data instanceof AiServiceInvocationData ai ? ai.mcpClientNames() : null;
    }

}
