package io.quarkiverse.langchain4j.mockapi.internal.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.server.HttpTestClient.Resp;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;

class MockApiServerTest {
    static MockApiServer server;
    static HttpTestClient http;

    @BeforeAll
    static void start() {
        server = new MockApiServer(OptionalInt.empty(), "default reply", 4, DialectRegistry.load());
        server.start();
        http = new HttpTestClient(server.port());
    }

    @AfterAll
    static void stop() throws Exception {
        http.close();
        server.close();
    }

    @BeforeEach
    void reset() {
        assertThat(http.post("/_admin/reset", "").statusCode()).isEqualTo(204);
    }

    @Test
    void healthListsDialects() {
        JsonNode dialects = body(http.get("/_admin/health")).at("/dialects");
        assertThat(dialects).extracting(JsonNode::asText).contains("openai", "anthropic", "watsonx", "bedrock");
    }

    @Test
    void queuedReplyThenDefault() {
        http.post("/_admin/replies/chat", "[{\"kind\":\"ChatReply\",\"text\":\"queued\"}]");
        assertThat(body(http.post("/v1/chat/completions", "{\"model\":\"m\",\"messages\":[]}"))
                .at("/choices/0/message/content").asText()).isEqualTo("queued");
        assertThat(body(http.post("/v1/chat/completions", "{\"model\":\"m\",\"messages\":[]}"))
                .at("/choices/0/message/content").asText()).isEqualTo("default reply");
    }

    @Test
    void journalHasTypedView() {
        http.post("/v1/chat/completions", "{\"model\":\"gpt\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        JsonNode reqs = body(http.get("/_admin/requests?operation=chat"));
        assertThat(reqs).hasSize(1);
        assertThat(reqs.at("/0/chat/messages/0/text").asText()).isEqualTo("hi");
        assertThat(reqs.at("/0/dialect").asText()).isEqualTo("openai");
    }

    @Test
    void unmatchedPathIs404WithDialects() {
        Resp r = http.post("/nowhere", "{}");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(body(r).at("/path").asText()).isEqualTo("/nowhere");
        assertThat(body(http.get("/_admin/requests"))).hasSize(1);
    }

    @Test
    void pathUnderAPrefixTheDialectRejectsIs404() {
        Resp r = http.post("/watsonx/ml/gateway/v1/images/generations", "{}");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(body(r).at("/path").asText()).isEqualTo("/watsonx/ml/gateway/v1/images/generations");
    }

    @Test
    void errorReply() {
        http.post("/_admin/replies/chat", "[{\"kind\":\"ErrorReply\",\"status\":429,\"message\":\"slow down\"}]");
        assertThat(http.post("/v1/chat/completions", "{}").statusCode()).isEqualTo(429);
    }

    @Test
    void unrenderableReplyIsA500InTheDialectsErrorEnvelope() {
        http.post("/_admin/replies/chat", "[{\"kind\":\"EmbeddingReply\",\"derivedDimension\":2}]");
        Resp r = http.post("/v1/chat/completions", "{}");
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(body(r).at("/error/message").asText()).contains("not supported");
        assertThat(body(r).at("/error/type").asText()).isEqualTo("mock_error");
    }

    @Test
    void ndjsonStreamIsOneJsonObjectPerLine() {
        http.post("/_admin/replies/chat", "[{\"kind\":\"ChatReply\",\"text\":\"hi there\"}]");
        Resp r = http.post("/ollama/api/chat", "{\"model\":\"m\",\"messages\":[]}");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.contentType()).isEqualTo("application/x-ndjson");
        assertThat(r.body()).endsWith("\n");
        List<JsonNode> lines = r.body().lines().map(MockApiServerTest::json).toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).at("/message/content").asText()).isEqualTo("hi");
        assertThat(lines.get(1).at("/message/content").asText()).isEqualTo(" there");
        assertThat(lines.get(2).at("/done").asBoolean()).isTrue();
    }

    @Test
    void rawStubWins() {
        http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/v1/chat/completions\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":202,\"headers\":{},\"body\":\"{\\\"raw\\\":true}\"}]}");
        Resp r = http.post("/v1/chat/completions", "{}");
        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(body(r).at("/raw").asBoolean()).isTrue();
    }

    @Test
    void fixedResponseNeedsNoReplyAndIsRecorded() {
        Resp r = http.send(HttpMethod.POST, "/watsonx/identity/token", "application/x-www-form-urlencoded",
                "grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey&apikey=k");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(body(r).at("/access_token").asText()).isEqualTo("mock-iam-token");

        JsonNode reqs = body(http.get("/_admin/requests"));
        assertThat(reqs).hasSize(1);
        assertThat(reqs.at("/0/path").asText()).isEqualTo("/watsonx/identity/token");
        assertThat(reqs.at("/0/dialect").asText()).isEqualTo("watsonx");
        assertThat(reqs.at("/0/body").asText()).contains("apikey=k");
    }

    @Test
    void rawStubBeatsFixedResponse() {
        http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/watsonx/identity/token\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":401,\"headers\":{},\"body\":\"{}\"}]}");
        assertThat(http.send(HttpMethod.POST, "/watsonx/identity/token", "application/x-www-form-urlencoded",
                "grant_type=g&apikey=k").statusCode()).isEqualTo(401);
    }

    @Test
    void rawStubAcceptsKindDiscriminator() {
        assertThat(http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/v1/chat/completions\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":203,\"headers\":{},\"body\":\"{}\"}]}")
                .statusCode()).isEqualTo(204);
        assertThat(http.post("/v1/chat/completions", "{}").statusCode()).isEqualTo(203);
    }

    @Test
    void delayedWriteFailureBecomes500() {
        // a header value with a line break makes ResponseWriter.write throw inside the timer callback
        http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/boom\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":200,\"headers\":{\"X-Bad\":\"a\\nb\"},\"body\":\"x\",\"delay\":\"PT0.1S\"}]}");
        Resp r = http.post("/boom", "{}");
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(body(r).at("/error").asText()).contains("internal error");
    }

    @Test
    void malformedRawStubPointerIsRejectedAndModelApiKeepsWorking() {
        Resp r = http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/v1/**\",\"bodyJsonPointer\":\"no-leading-slash\",\"bodyExpected\":\"x\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":202,\"headers\":{},\"body\":\"{}\"}]}");
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(body(r).at("/error").asText()).isNotEmpty();
        assertThat(http.post("/v1/chat/completions", "{\"messages\":[]}").statusCode()).isEqualTo(200);
    }

    @Test
    void delayedWriteAfterClientGaveUpIsSilent() throws Exception {
        List<Throwable> unhandled = new CopyOnWriteArrayList<>();
        server.vertx().exceptionHandler(unhandled::add);
        try {
            http.post("/_admin/replies/chat", "[{\"kind\":\"ChatReply\",\"text\":\"late\",\"delay\":\"PT0.3S\"}]");
            String body = "{\"stream\":true,\"messages\":[]}";
            try (Socket socket = new Socket("localhost", server.port())) {
                socket.getOutputStream().write(("POST /v1/chat/completions HTTP/1.1\r\nHost: localhost\r\n"
                        + "Content-Type: application/json\r\nContent-Length: " + body.length() + "\r\n\r\n" + body)
                        .getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                // the client gives up before the delayed reply is written
                Thread.sleep(100);
            }
            // let the delayed write run against the response the client abandoned
            Thread.sleep(600);
            assertThat(unhandled).isEmpty();
        } finally {
            server.vertx().exceptionHandler(null);
        }
    }

    @Test
    void putDefaultReplacesBuiltInDefault() {
        assertThat(http.put("/_admin/replies/chat/default", "{\"kind\":\"ChatReply\",\"text\":\"new default\"}").statusCode())
                .isEqualTo(204);
        for (int i = 0; i < 2; i++) {
            assertThat(body(http.post("/v1/chat/completions", "{\"messages\":[]}")).at("/choices/0/message/content").asText())
                    .isEqualTo("new default");
        }
    }

    @Test
    void noReplyConfiguredIs404() {
        Resp r = http.post("/v1/images/generations", "{}");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(body(r).at("/error").asText()).isEqualTo("no reply configured");
        assertThat(body(r).at("/operation").asText()).isEqualTo("IMAGE");
    }

    @Test
    void rawStubBeatsQueuedReplyWithoutConsumingIt() {
        http.post("/_admin/replies/chat", "[{\"kind\":\"ChatReply\",\"text\":\"queued\"}]");
        http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/v1/chat/completions\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":202,\"headers\":{},\"body\":\"{\\\"raw\\\":true}\"}]}");
        assertThat(body(http.post("/v1/chat/completions", "{}")).at("/raw").asBoolean()).isTrue();
        http.post("/_admin/reset", "");
        http.post("/_admin/replies/chat", "[{\"kind\":\"ChatReply\",\"text\":\"queued\"}]");
        http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/other\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":202,\"headers\":{},\"body\":\"{}\"}]}");
        http.post("/other", "{}");
        assertThat(body(http.post("/v1/chat/completions", "{}")).at("/choices/0/message/content").asText()).isEqualTo("queued");
    }

    @Test
    void responsesStreamUsesNamedEvents() {
        Resp r = http.post("/v1/responses", "{\"stream\":true,\"input\":\"hi\"}");
        assertThat(r.body()).contains("event: response.created");
    }

    @Test
    void adminErrors() {
        Resp badJson = http.post("/_admin/replies/chat", "not json");
        assertThat(badJson.statusCode()).isEqualTo(400);
        assertThat(body(badJson).at("/error").asText()).isNotEmpty();
        assertThat(http.post("/_admin/replies/nonsense", "[]").statusCode()).isEqualTo(400);
        Resp unknown = http.get("/_admin/whatever");
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(body(unknown).at("/error").asText()).isEqualTo("unknown admin path");
    }

    @Test
    void builtInEmbeddingsAndModerationDefaults() {
        JsonNode emb = body(http.post("/v1/embeddings", "{\"input\":[\"a\",\"b\"]}"));
        assertThat(emb.at("/data")).hasSize(2);
        assertThat(emb.at("/data/0/embedding")).hasSize(4);
        assertThat(body(http.post("/v1/moderations", "{\"input\":\"x\"}")).at("/results/0/flagged").asBoolean()).isFalse();
    }

    @Test
    void streamingIsServerSentEvents() {
        Resp r = http.post("/v1/chat/completions", "{\"stream\":true,\"messages\":[]}");
        assertThat(r.contentType()).startsWith("text/event-stream");
        assertThat(r.body()).contains("data: ").endsWith("data: [DONE]\n\n");
    }

    @Test
    void delayIsHonoured() {
        http.post("/_admin/replies/chat", "[{\"kind\":\"ChatReply\",\"text\":\"late\",\"delay\":\"PT0.3S\"}]");
        long t0 = System.nanoTime();
        http.post("/v1/chat/completions", "{}");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    private static JsonNode body(Resp resp) {
        return json(resp.body());
    }

    private static JsonNode json(String text) {
        try {
            return MockApiJson.mapper().readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void binaryBodyIsCapturedByteForByte() throws Exception {
        http.post("/_admin/raw-stubs",
                "{\"method\":\"POST\",\"pathGlob\":\"/v1/audio/transcriptions\",\"responses\":[{\"kind\":\"RawResponse\",\"status\":200,\"headers\":{},\"body\":\"{}\"}]}");
        String multipart = "--b\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n--b\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n"
                + "\u00ff\u0000\u0080\r\n--b--\r\n";
        byte[] bytes = multipart.getBytes(StandardCharsets.ISO_8859_1);
        http.send(HttpMethod.POST, "/v1/audio/transcriptions", "multipart/form-data; boundary=b", Buffer.buffer(bytes));

        JsonNode request = body(http.get("/_admin/requests")).get(0);
        String captured = request.get("body").asText();
        assertThat(captured).contains("name=\"model\"").contains("whisper-1").contains("filename=\"a.wav\"");
        assertThat(captured.getBytes(StandardCharsets.ISO_8859_1)).isEqualTo(bytes);
    }
}
