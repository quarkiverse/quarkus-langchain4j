package io.quarkiverse.langchain4j.test.coroutines;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowCollector;

/**
 * Verifies the support for AI service methods shaped like Kotlin {@code suspend} functions (a trailing
 * {@code kotlin.coroutines.Continuation} parameter) and methods returning {@code kotlinx.coroutines.flow.Flow}.
 * <p>
 * The interfaces in this test emulate, in Java, exactly the JVM signatures the Kotlin compiler produces, so the
 * support can be tested without adding a Kotlin compiler to the build.
 */
public class KotlinSuspendAndFlowAiServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SuspendAssistant.class, FlowAssistant.class,
                            RecordingChatModelSupplier.class, RecordingChatModel.class,
                            StreamingModelSupplier.class, StreamingModel.class));

    @Inject
    SuspendAssistant suspendAssistant;

    @Inject
    FlowAssistant flowAssistant;

    @Test
    @ActivateRequestContext
    void suspendFunctionResultIsDeliveredThroughTheContinuation() throws Exception {
        RecordingChatModel.reset();
        TestContinuation<String> continuation = new TestContinuation<>();
        Object returned = suspendAssistant.chat("hello", continuation);

        if (returned == coroutineSuspended()) {
            // the caller was asked to suspend: the continuation is resumed with the result once the invocation completes
            assertThat(continuation.await()).isTrue();
            assertThat(continuation.result()).isEqualTo("Hello from the model");
        } else {
            // the invocation completed before the caller had to suspend
            assertThat(returned).isEqualTo("Hello from the model");
            assertThat(continuation.resumed()).isFalse();
        }

        // the calling coroutine is never blocked: the model is invoked on a worker thread
        assertThat(RecordingChatModel.LAST_INVOCATION_THREAD.get())
                .isNotNull()
                .isNotSameAs(Thread.currentThread());
    }

    @Test
    @ActivateRequestContext
    void suspendFunctionFailureIsDeliveredThroughTheContinuation() throws Exception {
        TestContinuation<String> continuation = new TestContinuation<>();
        Object returned;
        try {
            returned = suspendAssistant.chat("please boom", continuation);
        } catch (RuntimeException e) {
            // the failure occurred before the caller had to suspend
            assertThat(e).hasMessageContaining("boom from the model");
            return;
        }

        assertThat(returned).isSameAs(coroutineSuspended());
        assertThat(continuation.await()).isTrue();
        // failures are resumed as a kotlin.Result.Failure
        assertThat(continuation.result().getClass().getName()).contains("Failure");
        assertThat(continuation.result().toString()).contains("boom from the model");
    }

    @Test
    @ActivateRequestContext
    void flowReturnTypeIsConvertedToAFlowAndCanBeCollected() throws Exception {
        Flow<String> flow = flowAssistant.stream("hello");
        // the Multi produced by the runtime must have been converted to a Kotlin Flow
        assertThat(flow).isNotInstanceOf(Multi.class);

        List<String> values = new CopyOnWriteArrayList<>();
        TestContinuation<Unit> collectionContinuation = new TestContinuation<>();
        Object collected = flow.collect(new FlowCollector<String>() {
            @Override
            public Object emit(String value, Continuation<? super Unit> continuation) {
                values.add(value);
                return Unit.INSTANCE;
            }
        }, collectionContinuation);

        if (collected == coroutineSuspended()) {
            assertThat(collectionContinuation.await()).isTrue();
        }
        assertThat(values).containsExactly("Hello", " ", "world!");
    }

    private static Object coroutineSuspended() throws Exception {
        return Class.forName("kotlin.coroutines.intrinsics.CoroutineSingletons")
                .getField("COROUTINE_SUSPENDED").get(null);
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingChatModelSupplier.class)
    public interface SuspendAssistant {

        // what `suspend fun chat(question: String): String` compiles to: the JVM method returns Object,
        // the actual return type is the type argument of the trailing Continuation parameter
        Object chat(String question, Continuation<? super String> continuation);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface FlowAssistant {

        // equivalent to: fun stream(question: String): Flow<String>
        Flow<String> stream(String question);
    }

    public static class RecordingChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new RecordingChatModel();
        }
    }

    public static class RecordingChatModel implements ChatModel {

        static final AtomicReference<Thread> LAST_INVOCATION_THREAD = new AtomicReference<>();

        static void reset() {
            LAST_INVOCATION_THREAD.set(null);
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            LAST_INVOCATION_THREAD.set(Thread.currentThread());
            if (lastUserMessage(chatRequest).contains("boom")) {
                throw new IllegalStateException("boom from the model");
            }
            return ChatResponse.builder().aiMessage(new AiMessage("Hello from the model")).build();
        }

        private static String lastUserMessage(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof UserMessage userMessage) {
                    return userMessage.singleText();
                }
            }
            return "";
        }
    }

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingModel();
        }
    }

    public static class StreamingModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hello");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("world!");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(new AiMessage(""))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.STOP)
                    .build());
        }
    }

    /**
     * A {@code kotlin.coroutines.Continuation} the test can await on. The result is either the value itself (Kotlin
     * erases {@code Result} to {@code Object} for the success case) or a {@code kotlin.Result.Failure} instance.
     */
    static class TestContinuation<T> implements Continuation<T> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Object result;

        @Override
        public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
        }

        @Override
        public void resumeWith(Object result) {
            this.result = result;
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(20, TimeUnit.SECONDS);
        }

        boolean resumed() {
            return latch.getCount() == 0;
        }

        Object result() {
            return result;
        }
    }
}
