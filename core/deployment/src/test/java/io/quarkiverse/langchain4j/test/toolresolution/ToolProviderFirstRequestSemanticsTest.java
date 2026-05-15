package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies the contract on the AiService method's tool-provider invocation:
 *
 * <ul>
 * <li>Static (non-dynamic) tool providers see a populated {@link ToolProviderRequest#messages()}
 * (non-null, non-empty) — i.e. {@code messagesToSend} is built BEFORE the static tool-provider
 * invocation and passed into the request.</li>
 * <li>Static providers are invoked exactly once per AiService method call (no double-invocation
 * via both the manual eager loop AND upstream {@code ToolService.createContext}).</li>
 * <li>Dynamic providers' tools are visible in the toolSpecifications of the FIRST chat request
 * (so the LLM can see them on iteration 0 — they're not gated until iteration 1).</li>
 * </ul>
 *
 * <p>
 * The test uses two static nested classes to keep the static-vs-dynamic test scopes
 * fully isolated, since Quarkus' AiService binding only registers one tool-provider supplier.
 */
public class ToolProviderFirstRequestSemanticsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RecordingChatModelSupplier.class, RecordingChatModel.class,
                            RecordingStaticToolProviderSupplier.class, RecordingStaticToolProvider.class,
                            RecordingDynamicToolProviderSupplier.class, RecordingDynamicToolProvider.class,
                            ServiceWithStaticProvider.class, ServiceWithDynamicProvider.class));

    @RegisterAiService(chatLanguageModelSupplier = RecordingChatModelSupplier.class, toolProviderSupplier = RecordingStaticToolProviderSupplier.class)
    interface ServiceWithStaticProvider {
        String chat(@UserMessage String msg, @MemoryId Object id);
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingChatModelSupplier.class, toolProviderSupplier = RecordingDynamicToolProviderSupplier.class)
    interface ServiceWithDynamicProvider {
        String chat(@UserMessage String msg, @MemoryId Object id);
    }

    @Inject
    ServiceWithStaticProvider staticService;

    @Inject
    ServiceWithDynamicProvider dynamicService;

    @BeforeEach
    void reset() {
        RecordingStaticToolProvider.invocations.set(0);
        RecordingStaticToolProvider.lastMessages.set(null);
        RecordingDynamicToolProvider.invocations.set(0);
        RecordingChatModel.firstRequestToolNames.set(null);
        RecordingChatModel.requestCount.set(0);
    }

    @Test
    @ActivateRequestContext
    void staticProviderSeesMessagesAndIsInvokedExactlyOnce() {
        String answer = staticService.chat("hello", 1);

        assertThat(answer).isEqualTo("STATIC");

        // Static provider invoked exactly once per AiService method call (no double-invocation
        // via both a manual eager loop AND upstream's ToolService.createContext).
        assertThat(RecordingStaticToolProvider.invocations.get())
                .as("static provider must be invoked exactly once per AiService call")
                .isEqualTo(1);

        // ToolProviderRequest.messages() must be non-null and non-empty — proves messagesToSend is
        // built BEFORE the static provider invocation and threaded through the factory.
        List<ChatMessage> messages = RecordingStaticToolProvider.lastMessages.get();
        assertThat(messages)
                .as("static provider must receive a non-null messages list")
                .isNotNull();
        assertThat(messages)
                .as("static provider must receive a non-empty messages list including the user message")
                .isNotEmpty();

        // Static provider's tool must be in the first chat request's toolSpecifications.
        assertThat(RecordingChatModel.firstRequestToolNames.get())
                .as("first chat request must include the static provider's tool")
                .contains("static_tool");
    }

    @Test
    @ActivateRequestContext
    void dynamicProviderToolsAreVisibleOnFirstRequest() {
        String answer = dynamicService.chat("hello", 1);

        assertThat(answer).isEqualTo("DYN");

        // Dynamic provider's tool must appear in the FIRST chat request's toolSpecifications —
        // proving dynamic providers are evaluated BEFORE the first inference request, not gated
        // until iteration 1.
        assertThat(RecordingChatModel.firstRequestToolNames.get())
                .as("first chat request must include the dynamic provider's tool")
                .contains("dynamic_tool");

        // The dynamic provider should have been called at least once before the first request,
        // and again on the follow-up iteration after tool execution.
        assertThat(RecordingDynamicToolProvider.invocations.get())
                .as("dynamic provider must be invoked at least once (for first request)")
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * ChatModel that records the toolSpecifications in the FIRST request and routes a single tool
     * call. Reads the last message and returns its text on follow-up so a tool execution result
     * propagates back as the AiService method's String return value.
     */
    public static class RecordingChatModel implements ChatModel {

        static final AtomicReference<List<String>> firstRequestToolNames = new AtomicReference<>();
        static final AtomicInteger requestCount = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            int n = requestCount.incrementAndGet();
            if (n == 1) {
                List<String> names = new ArrayList<>();
                if (chatRequest.toolSpecifications() != null) {
                    for (ToolSpecification spec : chatRequest.toolSpecifications()) {
                        names.add(spec.name());
                    }
                }
                firstRequestToolNames.set(names);
            }
            List<ChatMessage> messages = chatRequest.messages();
            ChatMessage last = messages.get(messages.size() - 1);
            if (last.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
                ToolExecutionResultMessage trm = (ToolExecutionResultMessage) last;
                return ChatResponse.builder().aiMessage(new AiMessage(trm.text())).build();
            }
            // First call → ask the LLM to invoke whichever tool is registered (there's only one).
            ToolSpecification tool = chatRequest.toolSpecifications().get(0);
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id(tool.name() + "-call")
                    .name(tool.name())
                    .arguments("{}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(req))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    }

    public static class RecordingChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new RecordingChatModel();
        }
    }

    /** Static (non-dynamic) tool provider that records call count and the messages it received. */
    @ApplicationScoped
    public static class RecordingStaticToolProvider implements ToolProvider {

        static final AtomicInteger invocations = new AtomicInteger();
        static final AtomicReference<List<ChatMessage>> lastMessages = new AtomicReference<>();

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            invocations.incrementAndGet();
            lastMessages.set(request.messages());
            ToolSpecification spec = ToolSpecification.builder()
                    .name("static_tool")
                    .description("Static tool")
                    .build();
            ToolExecutor exec = (call, memId) -> "STATIC";
            return ToolProviderResult.builder().add(spec, exec).build();
        }

        @Override
        public boolean isDynamic() {
            return false;
        }
    }

    @ApplicationScoped
    public static class RecordingStaticToolProviderSupplier implements Supplier<ToolProvider> {
        @Inject
        RecordingStaticToolProvider provider;

        @Override
        public ToolProvider get() {
            return provider;
        }
    }

    /** Dynamic tool provider — re-evaluated per loop iteration. */
    @ApplicationScoped
    public static class RecordingDynamicToolProvider implements ToolProvider {

        static final AtomicInteger invocations = new AtomicInteger();

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            invocations.incrementAndGet();
            ToolSpecification spec = ToolSpecification.builder()
                    .name("dynamic_tool")
                    .description("Dynamic tool")
                    .build();
            ToolExecutor exec = (call, memId) -> "DYN";
            return ToolProviderResult.builder().add(spec, exec).build();
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    @ApplicationScoped
    public static class RecordingDynamicToolProviderSupplier implements Supplier<ToolProvider> {
        @Inject
        RecordingDynamicToolProvider provider;

        @Override
        public ToolProvider get() {
            return provider;
        }
    }
}
