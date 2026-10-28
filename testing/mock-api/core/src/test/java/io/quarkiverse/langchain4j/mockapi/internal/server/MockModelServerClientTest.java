package io.quarkiverse.langchain4j.mockapi.internal.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.server.HttpTestClient.Resp;
import io.vertx.core.http.HttpMethod;

class MockModelServerClientTest {
    static MockApiServer server;
    static MockModelServer mock;
    static HttpTestClient http;

    @BeforeAll
    static void start() {
        server = new MockApiServer(OptionalInt.empty(), "default reply", 4, DialectRegistry.load());
        server.start();
        mock = MockModelServer.connect(server.url());
        http = new HttpTestClient(server.port());
    }

    @AfterAll
    static void stop() throws Exception {
        mock.close();
        http.close();
        server.close();
    }

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void chatReplyAndSingleRequest() {
        mock.chat().reply("queued");
        postChat("{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.asChat().messages().get(0).text()).isEqualTo("hi");
        assertThat(r.header("content-type")).contains("json");
    }

    @Test
    void singleRequestFailsOnZero() {
        assertThatThrownBy(() -> mock.chat().singleRequest()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("found 0");
    }

    @Test
    void failAndDefault() {
        mock.chat().fail(500, "boom");
        mock.chat().defaultReply(ChatReply.text("dflt"));
        assertThat(postChat("{}").statusCode()).isEqualTo(500);
        assertThat(postChatText("{}")).isEqualTo("dflt");
    }

    @Test
    void rawStub() {
        mock.raw().on("POST", "/x/**").reply(202, "{\"ok\":true}");
        assertThat(http.send(HttpMethod.POST, "/x/y/z", "application/json", "{}").statusCode()).isEqualTo(202);
    }

    @Test
    void rawStubWhenBody() {
        mock.raw().on("POST", "/w").whenBody("/kind", "a").reply(200, "{\"r\":\"a\"}");
        mock.raw().on("POST", "/w").whenBody("/kind", "b").reply(201, "{\"r\":\"b\"}");
        assertThat(http.send(HttpMethod.POST, "/w", "application/json", "{\"kind\":\"b\"}").statusCode()).isEqualTo(201);
        assertThat(http.send(HttpMethod.POST, "/w", "application/json", "{\"kind\":\"a\"}").statusCode()).isEqualTo(200);
    }

    @Test
    void rawStubDelay() {
        mock.raw().on("GET", "/slow").delay(Duration.ofMillis(300)).reply(200, "{}");
        long start = System.nanoTime();
        assertThat(http.get("/slow").statusCode()).isEqualTo(200);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    @Test
    void rawStubRepliesInOrder() {
        mock.raw().on("GET", "/job").replies(RawResponse.json(202, "{\"s\":\"running\"}"),
                RawResponse.sse(200, Map.of(), List.of(SseEvent.of("done", "{\"s\":\"done\"}"))));
        assertThat(http.get("/job").statusCode()).isEqualTo(202);
        Resp done = http.get("/job");
        assertThat(done.contentType()).contains("text/event-stream");
        assertThat(done.body()).contains("event: done").contains("{\"s\":\"done\"}");
        assertThat(http.get("/job").contentType()).contains("text/event-stream");
    }

    @Test
    void scoringScope() {
        mock.scoring().reply(ScoringReply.of(0.9, 0.1));
        mock.scoring().defaultReply(ScoringReply.equalScores());
        assertThat(mock.scoring().requests()).isEmpty();
        assertThatThrownBy(() -> mock.scoring().singleRequest()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("SCORING");
    }

    @Test
    void replyOnNonChatScopeRejected() {
        assertThatThrownBy(() -> mock.embeddings().reply("x")).isInstanceOf(IllegalStateException.class);
    }

    private static Resp postChat(String body) {
        return http.send(HttpMethod.POST, "/v1/chat/completions", "application/json", body);
    }

    private static String postChatText(String body) {
        try {
            return MockApiJson.mapper().readTree(postChat(body).body()).at("/choices/0/message/content").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
