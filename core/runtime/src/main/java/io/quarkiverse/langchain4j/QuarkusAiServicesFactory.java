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
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.spi.services.AiServicesFactory;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemorySeeder;
import io.quarkiverse.langchain4j.runtime.aiservice.ParallelToolExecutorResolver;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusToolProviderRequest;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProvider;
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
            Executor parallelExecutor = null;
            try {
                LangChain4jConfig langChain4jConfig = ConfigProvider.getConfig()
                        .unwrap(SmallRyeConfig.class)
                        .getConfigMapping(LangChain4jConfig.class);
                parallelExecutor = ParallelToolExecutorResolver.resolve(aiServiceClass.getName(), langChain4jConfig);
            } catch (RuntimeException e) {
                // Defensive: never let executor resolution prevent AiService construction. Logging here mirrors the
                // resolver's existing fallback-to-serial philosophy.
                LOG.warnf(e,
                        "Failed to resolve parallel-tool executor for AiService '%s'; falling back to serial dispatch.",
                        aiServiceClass.getName());
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
     * Wires the four upstream-{@code 1.14.0+} {@link AiServices} hooks the Quarkus extension uses to
     * delegate the LLM&#8596;tools loop while preserving Quarkus-specific behaviours:
     * <ul>
     * <li>{@code executeToolsConcurrently(parallelExecutor)} — when a parallel mode is enabled.
     * The executor is already wrapped in {@code VertxContextAwareExecutor}, so each task
     * re-enters the caller's Vert.x duplicated context (chat memory, request scope, OTel,
     * MDC) before running.</li>
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
        aiServiceBuilder.forceToolChoiceAutoAfterFirstIteration(!context.allowContinuousForcedToolCalling);
        aiServiceBuilder.errorHandlerBypass(t -> t instanceof PreventsErrorHandlerExecution);
        aiServiceBuilder.toolProviderRequestFactory(builder -> {
            // mcpClientNames live on the per-method AiServiceMethodCreateInfo. The upstream ToolService
            // calls this factory both at initial context creation and during dynamic provider refresh,
            // passing a fully populated builder. We build the upstream request, then look up the
            // per-method mcpClientNames via the AiServicesRecorder metadata using the
            // InvocationContext.interfaceName + methodName carried on the request.
            dev.langchain4j.service.tool.ToolProviderRequest req = builder.build();
            return new QuarkusToolProviderRequest(req.invocationContext(), req.userMessage(),
                    mcpClientNamesFor(req.invocationContext()));
        });
    }

    /**
     * Returns the per-method {@code mcpClientNames} list straight from the build-time metadata, or
     * {@code null} when the method has no {@code @McpToolBox} annotation. Nullability is meaningful:
     * MCP's filter ({@code QuarkusMcpToolProvider.McpClientKeyFilter}) reads {@code keys == null}
     * as "no MCP clients selected" (the @McpToolBox-absent default) and an empty list as
     * "select all MCP clients". Returning an empty list when the metadata is null would silently
     * widen scope, surfacing every MCP tool to a method that previously saw none.
     */
    private static java.util.List<String> mcpClientNamesFor(dev.langchain4j.invocation.InvocationContext ic) {
        if (ic == null) {
            return null;
        }
        try {
            AiServiceClassCreateInfo classInfo = AiServicesRecorder.getMetadata().get(ic.interfaceName());
            if (classInfo == null) {
                return null;
            }
            for (AiServiceMethodCreateInfo method : classInfo.methodMap().values()) {
                if (method.getMethodName().equals(ic.methodName())) {
                    return method.getMcpClientNames();
                }
            }
        } catch (RuntimeException ignored) {
            // Best-effort: a missing invocation context simply means no MCP-name scoping for this call.
        }
        return null;
    }

}
