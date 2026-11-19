package io.quarkiverse.langchain4j.test.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * LangChain4j 1.24.0 links the work of an AI service invocation to the invocation itself: the
 * {@code ChatModelListener} contexts of the chat model calls made for an invocation carry its
 * {@code invocationId()}, an AI service invoked from a tool of another invocation records that invocation as its
 * {@code parentInvocationId()}, and the {@code InvocationContext} carries the {@code methodReturnType()}.
 * <p>
 * The extension builds the {@code InvocationContext} and calls the chat model itself, so it must set all of that up
 * the way {@code DefaultAiServices} does.
 */
public class CurrentInvocationLinkageTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(OuterAiService.class, InnerAiService.class, InnerCallingTool.class,
                            MyChatModel.class, MyChatModelSupplier.class, Recorder.class));

    @Inject
    OuterAiService outer;

    @Inject
    InnerAiService inner;

    @Inject
    Recorder recorder;

    @BeforeEach
    void clearRecorder() {
        recorder.clear();
    }

    @Test
    @ActivateRequestContext
    void chatModelListenerContextsCarryTheInvocationId() {
        assertThat(inner.chat("hello")).isEqualTo("Hi!");

        assertThat(recorder.started).hasSize(1);
        UUID invocationId = recorder.started.get(0).invocationId();
        assertThat(invocationId).isNotNull();

        assertThat(Recorder.MODEL_REQUEST_INVOCATION_IDS).containsExactly(invocationId);
        assertThat(Recorder.MODEL_RESPONSE_INVOCATION_IDS).containsExactly(invocationId);
    }

    @Test
    @ActivateRequestContext
    void invocationContextCarriesTheMethodReturnType() {
        assertThat(inner.chat("hello")).isEqualTo("Hi!");

        assertThat(recorder.started).hasSize(1);
        assertThat(recorder.started.get(0).methodReturnType()).isEqualTo(String.class);
    }

    @Test
    @ActivateRequestContext
    void aiServiceInvokedFromAToolRecordsTheParentInvocation() {
        assertThat(outer.chat("hello")).isEqualTo("Hi!");

        // the outer invocation, and the inner one made by the tool of the outer one
        assertThat(recorder.started).hasSize(2);
        InvocationContext outerContext = recorder.contextOf(OuterAiService.class);
        InvocationContext innerContext = recorder.contextOf(InnerAiService.class);

        assertThat(outerContext.parentInvocationId()).isNull();
        assertThat(innerContext.parentInvocationId()).isEqualTo(outerContext.invocationId());
    }

    @Test
    @ActivateRequestContext
    void chatModelListenerContextsCarryTheInvocationIdAcrossToolRoundTrips() {
        assertThat(outer.chat("hello")).isEqualTo("Hi!");

        InvocationContext outerContext = recorder.contextOf(OuterAiService.class);
        InvocationContext innerContext = recorder.contextOf(InnerAiService.class);

        // three model calls: the outer one that asked for the tool, the inner one made by the tool, and the
        // outer one that got the tool result — each tagged with the invocation it was made for
        assertThat(Recorder.MODEL_REQUEST_INVOCATION_IDS).containsExactly(
                outerContext.invocationId(), innerContext.invocationId(), outerContext.invocationId());
        assertThat(Recorder.MODEL_RESPONSE_INVOCATION_IDS).containsExactly(
                outerContext.invocationId(), innerContext.invocationId(), outerContext.invocationId());
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, tools = InnerCallingTool.class)
    public interface OuterAiService {

        String chat(@UserMessage String message);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface InnerAiService {

        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    public static class InnerCallingTool {

        @Inject
        InnerAiService inner;

        @Tool
        public String callInner() {
            return inner.chat("from the tool");
        }
    }

    @Singleton
    public static class Recorder {

        static final List<UUID> MODEL_REQUEST_INVOCATION_IDS = new CopyOnWriteArrayList<>();
        static final List<UUID> MODEL_RESPONSE_INVOCATION_IDS = new CopyOnWriteArrayList<>();

        final List<InvocationContext> started = new CopyOnWriteArrayList<>();

        void onStarted(@Observes AiServiceStartedEvent event) {
            started.add(event.invocationContext());
        }

        InvocationContext contextOf(Class<?> aiService) {
            return started.stream()
                    .filter(context -> aiService.getName().equals(context.interfaceName()))
                    .findFirst()
                    .orElseThrow();
        }

        void clear() {
            started.clear();
            MODEL_REQUEST_INVOCATION_IDS.clear();
            MODEL_RESPONSE_INVOCATION_IDS.clear();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ToolSpecification> tools = chatRequest.toolSpecifications();
            if (tools != null && !tools.isEmpty() && chatRequest.messages().size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("callInner")
                                .arguments("{}")
                                .build()))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("Hi!")).build();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return List.of(new ChatModelListener() {
                @Override
                public void onRequest(ChatModelRequestContext requestContext) {
                    Recorder.MODEL_REQUEST_INVOCATION_IDS.add(requestContext.invocationId());
                }

                @Override
                public void onResponse(ChatModelResponseContext responseContext) {
                    Recorder.MODEL_RESPONSE_INVOCATION_IDS.add(responseContext.invocationId());
                }
            });
        }
    }
}
