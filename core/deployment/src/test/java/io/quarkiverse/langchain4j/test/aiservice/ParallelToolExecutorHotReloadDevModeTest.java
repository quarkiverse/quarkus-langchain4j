package io.quarkiverse.langchain4j.test.aiservice;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Dev-mode regression test for the parallel-tool executor hot-reload lifecycle.
 *
 * <p>
 * The {@code ParallelToolExecutorResolver} is an Arc-owned {@code @Singleton}, so its cached executor wrappers
 * (including the shared virtual-thread executor) are tied to the current application generation. On hot reload
 * Quarkus disposes the Arc container and runs {@code VirtualThreadsRecorder}'s dev-mode shutdown task, which
 * shuts the current virtual-thread executor down. The new generation must build a fresh resolver and obtain a
 * fresh executor from {@link io.quarkus.virtual.threads.VirtualThreadsRecorder#getCurrent()} — a stale wrapper
 * retained across generations would dispatch to a shut-down delegate and fail every parallel tool batch.
 *
 * <p>
 * A {@code QuarkusUnitTest} that manually calls {@code Arc.shutdown()} is not equivalent: it does not exercise
 * {@code VirtualThreadsRecorder}'s dev-mode shutdown task, which is what makes the underlying
 * {@code ThreadPerTaskExecutor} dead in the first place.
 */
public class ParallelToolExecutorHotReloadDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ParallelToolsResource.class, ParallelToolsService.class, ParallelTools.class,
                            ThreeToolBatchModelSupplier.class, ThreeToolBatchModel.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.tools.execution=virtual-threads\n"),
                            "application.properties"));

    @Test
    public void parallelToolDispatchSurvivesHotReload() {
        // The bug is only reachable when virtual-threads mode actually engages. On Java 17-20 the resolver
        // downgrades VT mode to serial inside applyJavaVersionFallback before constructing BoundedExecutor,
        // so VirtualThreadsRecorder.getCurrent() is never called and the stale-delegate bug cannot reproduce.
        assumeTrue(Runtime.version().feature() >= 21,
                "Parallel-tool hot-reload regression requires Java 21+ — on earlier JVMs VT mode downgrades to "
                        + "serial dispatch, so the stale virtual-thread executor cannot be reached.");

        // First request — primes the VT executor in the current application generation.
        get("/parallel-tools/chat").then().statusCode(200).body(equalTo("done"));

        // Trigger a hot reload by appending to application.properties — any resource change forces Quarkus to
        // dispose the current Arc container and run VirtualThreadsRecorder's dev-mode shutdown task before
        // serving the next request.
        devModeTest.modifyResourceFile("application.properties",
                src -> src + "# touched-to-trigger-hot-reload\n");

        // Several follow-up requests so transient post-reload jitter can't be mistaken for a regression.
        for (int i = 0; i < 3; i++) {
            get("/parallel-tools/chat").then().statusCode(200).body(equalTo("done"));
        }
    }

    @Path("/parallel-tools")
    public static class ParallelToolsResource {

        private final ParallelToolsService service;

        public ParallelToolsResource(ParallelToolsService service) {
            this.service = service;
        }

        @GET
        @Path("/chat")
        public String chat() {
            return service.chat("go");
        }
    }

    @ApplicationScoped
    public static class ParallelTools {

        static final AtomicInteger invocations = new AtomicInteger();

        @Tool
        public String tool1() {
            invocations.incrementAndGet();
            return "ok-1";
        }

        @Tool
        public String tool2() {
            invocations.incrementAndGet();
            return "ok-2";
        }

        @Tool
        public String tool3() {
            invocations.incrementAndGet();
            return "ok-3";
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ThreeToolBatchModelSupplier.class, tools = ParallelTools.class)
    public interface ParallelToolsService {
        String chat(@UserMessage String message);
    }

    public static class ThreeToolBatchModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ThreeToolBatchModel();
        }
    }

    public static class ThreeToolBatchModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (messages.size() == 1) {
                List<ToolExecutionRequest> reqs = new ArrayList<>();
                reqs.add(ToolExecutionRequest.builder().id("a").name("tool1").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("b").name("tool2").arguments("{}").build());
                reqs.add(ToolExecutionRequest.builder().id("c").name("tool3").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(reqs))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }
}
