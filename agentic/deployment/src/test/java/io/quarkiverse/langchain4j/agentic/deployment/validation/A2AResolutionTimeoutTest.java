package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.a2a.A2AAgentCardException;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

/**
 * The fetch of the agent card of an A2A agent is given the time set with {@code resolutionTimeoutSeconds}: an A2A
 * server that accepts the connection and never answers fails the invocation after that time, instead of blocking it.
 */
public class A2AResolutionTimeoutTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest();

    /**
     * An A2A server that accepts the requests and never answers them.
     */
    @ApplicationScoped
    public static class SilentA2AServer {

        @Inject
        Vertx vertx;

        private HttpServer server;

        @PostConstruct
        void start() throws Exception {
            server = vertx.createHttpServer()
                    .requestHandler(new Handler<HttpServerRequest>() {
                        @Override
                        public void handle(HttpServerRequest request) {
                            // the request is left without a response
                        }
                    })
                    .listen(0)
                    .toCompletionStage().toCompletableFuture().get(10, SECONDS);
        }

        @PreDestroy
        void stop() throws Exception {
            server.close().toCompletionStage().toCompletableFuture().get(10, SECONDS);
        }

        public String url() {
            return "http://localhost:" + server.actualPort();
        }
    }

    public interface RemoteWriter {

        @A2AClientAgent(description = "Writes a story remotely", outputKey = "story", resolutionTimeoutSeconds = 1)
        String writeStory(@V("topic") String topic);

        @A2AServerUrlSupplier
        static String a2aServerUrl(@CdiBean SilentA2AServer server) {
            return server.url();
        }
    }

    public interface StoryCreator {

        @SequenceAgent(outputKey = "story", subAgents = RemoteWriter.class)
        String write(@V("topic") String topic);
    }

    @Inject
    StoryCreator storyCreator;

    @Inject
    SilentA2AServer server;

    @Test
    void cardFetchFailsAfterTheResolutionTimeout() {
        long start = System.nanoTime();

        assertThatThrownBy(new ThrowingCallable() {
            @Override
            public void call() {
                storyCreator.write("dragons and wizards");
            }
        }).isInstanceOf(AgentInvocationException.class)
                .hasStackTraceContaining(A2AAgentCardException.class.getName()
                        + ": Failed to fetch the agent card of A2A agent 'writeStory' from " + server.url() + " within 1 s")
                .hasStackTraceContaining("Caused by: " + TimeoutException.class.getName());

        // the time of the annotation, and not the default of 30 seconds
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }
}
