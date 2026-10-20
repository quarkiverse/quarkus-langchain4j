package io.quarkiverse.langchain4j.mockapi.internal.dialect.watsonx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class WatsonxDialectTest {

    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, "text-chat");
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, "text-chat");
    private static final Dialect.Match DEPLOYMENT = new Dialect.Match(Operation.CHAT, false, "deployment/my-deployment");
    private static final Dialect.Match EMBEDDINGS = new Dialect.Match(Operation.EMBEDDINGS, false, "embeddings");
    private static final Dialect.Match RERANK = new Dialect.Match(Operation.SCORING, false, "rerank");
    private static final Dialect.Match DETECTION = new Dialect.Match(Operation.MODERATION, false, "detection");
    private static final Dialect.Match GATEWAY_CHAT = new Dialect.Match(Operation.CHAT, false, "gateway");
    private static final Dialect.Match GATEWAY_STREAM = new Dialect.Match(Operation.CHAT, true, "gateway");
    private static final Dialect.Match GATEWAY_EMBEDDINGS = new Dialect.Match(Operation.EMBEDDINGS, false, "gateway");

    WatsonxDialect d = new WatsonxDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(7, "POST", "/watsonx/ml/v1/text/chat", "version=2025-04-23", Map.of(), body,
                Operation.CHAT, "watsonx", null, null);
    }

    @Test
    void matchesTheModelOperationsUnderItsPrefix() {
        assertThat(d.id()).isEqualTo("watsonx");
        assertThat(d.pathPrefix()).isEqualTo("/watsonx");
        assertThat(d.match("POST", "/ml/v1/text/chat", Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", "/ml/v1/text/chat_stream", Map.of(), null)).contains(STREAM);
        assertThat(d.match("POST", "/ml/v1/deployments/my-deployment/text/chat", Map.of(), null)).contains(DEPLOYMENT);
        assertThat(d.match("POST", "/ml/v1/deployments/my-deployment/text/chat_stream", Map.of(), null))
                .contains(new Dialect.Match(Operation.CHAT, true, "deployment/my-deployment"));
        assertThat(d.match("POST", "/ml/v1/text/embeddings", Map.of(), null)).contains(EMBEDDINGS);
        assertThat(d.match("POST", "/ml/v1/text/rerank", Map.of(), null)).contains(RERANK);
        assertThat(d.match("POST", "/ml/v1/text/detection", Map.of(), null)).contains(DETECTION);
        assertThat(d.match("POST", "/ml/gateway/v1/chat/completions", Map.of(), null)).contains(GATEWAY_CHAT);
        assertThat(d.match("POST", "/ml/gateway/v1/chat/completions", Map.of(), json("{\"stream\":true}")))
                .contains(GATEWAY_STREAM);
        assertThat(d.match("POST", "/ml/gateway/v1/embeddings", Map.of(), null)).contains(GATEWAY_EMBEDDINGS);

        assertThat(d.match("GET", "/ml/v1/text/chat", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/ml/v1/text/tokenization", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/ml/v1/text/generation", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/ml/v1/deployments/x/text/generation", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/identity/token", Map.of(), null)).isEmpty();
    }

    @Test
    void watsonxRequestsAreResolvedByPrefix() {
        assertThat(DialectRegistry.load().resolve("POST", "/watsonx/ml/v1/text/chat", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("watsonx");
        // the gateway path ends like OpenAI's, but the prefix decides, and sends it to watsonx
        assertThat(DialectRegistry.load().resolve("POST", "/watsonx/ml/gateway/v1/chat/completions", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("watsonx");
    }

    @Test
    void iamTokenIsAlwaysGrantedThroughTheRegistry() throws Exception {
        long before = Instant.now().getEpochSecond();
        DialectRegistry.Fixed fixed = DialectRegistry.load()
                .fixedResponse("POST", "/watsonx/identity/token",
                        "grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey&apikey=mock-api-key")
                .orElseThrow();

        assertThat(fixed.dialect().id()).isEqualTo("watsonx");
        Rendered r = fixed.rendered();
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/access_token").asText()).isEqualTo("mock-iam-token");
        assertThat(j.at("/refresh_token").asText()).isEqualTo("not_supported");
        assertThat(j.at("/token_type").asText()).isEqualTo("bearer");
        assertThat(j.at("/expires_in").asLong()).isPositive();
        // far in the future, so the client never asks for a new token during a test run
        assertThat(j.at("/expiration").asLong()).isGreaterThan(before + 365L * 24 * 3600);
        assertThat(j.at("/expiration").asLong() - j.at("/expires_in").asLong()).isBetween(before, before + 60);
    }

    @Test
    void iamTokenRequestWithoutTheRequiredFormFieldsIsRejected() throws Exception {
        Rendered r = d.fixedResponse("POST", "/identity/token", "grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey")
                .orElseThrow();

        assertThat(r.status()).isEqualTo(400);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/errorCode").asText()).isNotBlank();
        assertThat(j.at("/errorMessage").asText()).contains("apikey");
    }

    @Test
    void onlyTheIamTokenHasAFixedResponse() {
        assertThat(d.fixedResponse("GET", "/identity/token", "")).isEmpty();
        assertThat(d.fixedResponse("POST", "/ml/v1/text/chat", "{}")).isEmpty();
        assertThat(DialectRegistry.load().fixedResponse("POST", "/identity/token", "apikey=k&grant_type=g")).isEmpty();
    }

    @Test
    void parsesTextChatRequest() {
        ChatRequestView v = d.parseChat(CHAT, json("""
                {"model_id":"ibm/granite-4-h-small","project_id":"p","temperature":0.3,"top_p":0.9,
                 "max_completion_tokens":100,"max_tokens":5,
                 "messages":[
                   {"role":"system","content":"be nice"},
                   {"role":"user","content":[{"type":"text","text":"hi "},{"type":"image_url","image_url":{"url":"x"}},
                                             {"type":"text","text":"there"}]},
                   {"role":"assistant","tool_calls":[{"id":"c1","type":"function",
                     "function":{"name":"getWeather","arguments":"{\\"city\\":\\"Paris\\"}"}}]},
                   {"role":"tool","content":"rainy","tool_call_id":"c1"}],
                 "tools":[{"type":"function","function":{"name":"getWeather","description":"weather",
                   "parameters":{"type":"object","properties":{"city":{"type":"string"}}}}}],
                 "tool_choice_option":"required",
                 "response_format":{"type":"json_schema","json_schema":{"name":"s","schema":{"type":"object"}}}}
                """));

        assertThat(v.modelName()).isEqualTo("ibm/granite-4-h-small");
        assertThat(v.stream()).isFalse();
        assertThat(v.temperature()).isEqualTo(0.3);
        assertThat(v.topP()).isEqualTo(0.9);
        // max_tokens is ignored when max_completion_tokens is given
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.messages()).hasSize(4);
        assertThat(v.messages().get(0).role()).isEqualTo("system");
        assertThat(v.messages().get(0).text()).isEqualTo("be nice");
        assertThat(v.messages().get(1).text()).isEqualTo("hi there");
        assertThat(v.messages().get(2).toolCalls()).singleElement().satisfies(tc -> {
            assertThat(tc.id()).isEqualTo("c1");
            assertThat(tc.name()).isEqualTo("getWeather");
            assertThat(tc.argumentsJson()).isEqualTo("{\"city\":\"Paris\"}");
        });
        assertThat(v.messages().get(3).role()).isEqualTo("tool");
        assertThat(v.messages().get(3).toolCallId()).isEqualTo("c1");
        assertThat(v.messages().get(3).text()).isEqualTo("rainy");
        assertThat(v.tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.description()).isEqualTo("weather");
            assertThat(t.parametersSchema().at("/properties/city/type").asText()).isEqualTo("string");
        });
        assertThat(v.toolChoice()).isEqualTo("required");
        assertThat(v.responseFormatType()).isEqualTo("json_schema");
        assertThat(v.responseFormatSchema().at("/type").asText()).isEqualTo("object");
    }

    @Test
    void parsesModelAliasMaxTokensAndNamedToolChoice() {
        ChatRequestView v = d.parseChat(STREAM, json("""
                {"model":"m","max_tokens":5,"messages":[{"role":"user","content":"hi"}],
                 "tool_choice":{"type":"function","function":{"name":"getWeather"}}}
                """));

        assertThat(v.modelName()).isEqualTo("m");
        assertThat(v.stream()).isTrue();
        assertThat(v.maxTokens()).isEqualTo(5);
        assertThat(v.toolChoice()).isEqualTo("getWeather");
        assertThat(v.messages()).singleElement().satisfies(msg -> assertThat(msg.text()).isEqualTo("hi"));
    }

    @Test
    void parsesDeploymentChatWithoutAModel() {
        ChatRequestView v = d.parseChat(DEPLOYMENT, json("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));

        assertThat(v.modelName()).isNull();
        assertThat(v.messages()).singleElement().satisfies(msg -> assertThat(msg.text()).isEqualTo("hi"));
    }

    @Test
    void rendersTextChat() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("Hello").usage(3, 5),
                req("{\"model_id\":\"ibm/granite\",\"messages\":[]}"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/id").asText()).isEqualTo("chat-mock-7");
        assertThat(j.at("/model_id").asText()).isEqualTo("ibm/granite");
        assertThat(j.at("/model").asText()).isEqualTo("ibm/granite");
        assertThat(j.at("/created").asLong()).isPositive();
        assertThat(Instant.parse(j.at("/created_at").asText())).isNotNull();
        assertThat(j.at("/choices")).hasSize(1);
        assertThat(j.at("/choices/0/index").asInt()).isZero();
        assertThat(j.at("/choices/0/message/role").asText()).isEqualTo("assistant");
        assertThat(j.at("/choices/0/message/content").asText()).isEqualTo("Hello");
        assertThat(j.at("/choices/0/message").has("tool_calls")).isFalse();
        assertThat(j.at("/choices/0/finish_reason").asText()).isEqualTo("stop");
        assertThat(j.at("/usage/prompt_tokens").asInt()).isEqualTo(3);
        assertThat(j.at("/usage/completion_tokens").asInt()).isEqualTo(5);
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(8);
    }

    @Test
    void rendersToolCalls() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.toolCall("getWeather", Map.of("city", "Paris")),
                req("{\"model_id\":\"m\",\"messages\":[]}"));

        JsonNode j = m.readTree(r.body());
        JsonNode call = j.at("/choices/0/message/tool_calls/0");
        assertThat(call.at("/id").asText()).isEqualTo("call_mock_0");
        assertThat(call.at("/type").asText()).isEqualTo("function");
        assertThat(call.at("/function/name").asText()).isEqualTo("getWeather");
        assertThat(m.readTree(call.at("/function/arguments").asText()).at("/city").asText()).isEqualTo("Paris");
        assertThat(j.at("/choices/0/message").has("content")).isFalse();
        assertThat(j.at("/choices/0/finish_reason").asText()).isEqualTo("tool_calls");
    }

    @Test
    void mapsFinishReasonsOntoTheDocumentedValues() throws Exception {
        assertThat(finishReason(FinishReason.LENGTH)).isEqualTo("length");
        // the API documents no content filter reason
        assertThat(finishReason(FinishReason.CONTENT_FILTER)).isEqualTo("error");
    }

    private String finishReason(FinishReason reason) throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("x").finishReason(reason), req("{\"model_id\":\"m\"}"));
        return m.readTree(r.body()).at("/choices/0/finish_reason").asText();
    }

    @Test
    void replyModelNameWinsAndDeploymentFallsBackToTheDeploymentId() throws Exception {
        assertThat(m.readTree(d.render(CHAT, ChatReply.text("x").modelName("other"), req("{\"model_id\":\"m\"}")).body())
                .at("/model_id").asText()).isEqualTo("other");
        assertThat(m.readTree(d.render(DEPLOYMENT, ChatReply.text("x"), req("{\"messages\":[]}")).body())
                .at("/model_id").asText()).isEqualTo("my-deployment");
    }

    @Test
    void streamsTextAsMessageEventsWithUsageOnTheLastChunk() throws Exception {
        Rendered r = d.render(STREAM, ChatReply.text("As an AI").usage(2, 3), req("{\"model_id\":\"m\"}"));

        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        assertThat(r.events()).allSatisfy(e -> assertThat(e.event()).isEqualTo("message"));
        List<JsonNode> chunks = chunks(r);
        assertThat(chunks).hasSize(4);
        StringBuilder text = new StringBuilder();
        for (JsonNode c : chunks) {
            assertThat(c.at("/id").asText()).isEqualTo("chat-mock-7");
            assertThat(c.at("/model_id").asText()).isEqualTo("m");
            assertThat(c.at("/created").asLong()).isPositive();
            assertThat(c.at("/choices")).hasSize(1);
            assertThat(c.at("/choices/0/delta/role").asText()).isEqualTo("assistant");
            text.append(c.at("/choices/0/delta/content").asText(""));
        }
        assertThat(text.toString()).isEqualTo("As an AI");
        assertThat(chunks.get(0).at("/choices/0/finish_reason").isNull()).isTrue();
        assertThat(chunks.get(0).has("usage")).isFalse();
        JsonNode last = chunks.get(3);
        assertThat(last.at("/choices/0/finish_reason").asText()).isEqualTo("stop");
        assertThat(last.at("/usage/prompt_tokens").asInt()).isEqualTo(2);
        assertThat(last.at("/usage/completion_tokens").asInt()).isEqualTo(3);
        assertThat(last.at("/usage/total_tokens").asInt()).isEqualTo(5);
    }

    @Test
    void streamsToolCallsWithTheirIndex() throws Exception {
        Rendered r = d.render(STREAM, ChatReply.toolCall("getWeather", Map.of("city", "Paris")), req("{\"model_id\":\"m\"}"));

        List<JsonNode> chunks = chunks(r);
        assertThat(chunks).hasSize(2);
        JsonNode call = chunks.get(0).at("/choices/0/delta/tool_calls/0");
        assertThat(call.at("/index").asInt()).isZero();
        assertThat(call.at("/id").asText()).isEqualTo("call_mock_0");
        assertThat(call.at("/type").asText()).isEqualTo("function");
        assertThat(call.at("/function/name").asText()).isEqualTo("getWeather");
        assertThat(m.readTree(call.at("/function/arguments").asText()).at("/city").asText()).isEqualTo("Paris");
        assertThat(chunks.get(1).at("/choices/0/finish_reason").asText()).isEqualTo("tool_calls");
    }

    @Test
    void emptyStreamStillHasTheFinalChunk() throws Exception {
        List<JsonNode> chunks = chunks(d.render(STREAM, ChatReply.text(""), req("{\"model_id\":\"m\"}")));

        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.at("/choices/0/delta/role").asText()).isEqualTo("assistant");
            assertThat(c.at("/choices/0/finish_reason").asText()).isEqualTo("stop");
            assertThat(c.has("usage")).isTrue();
        });
    }

    @Test
    void rendersEmbeddingsPerInput() throws Exception {
        Rendered r = d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 0.1f, 0.2f }, new float[] { 0.3f, 0.4f }),
                req("{\"model_id\":\"ibm/slate\",\"inputs\":[\"a\",\"b\"]}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/model_id").asText()).isEqualTo("ibm/slate");
        assertThat(j.at("/results")).hasSize(2);
        assertThat(j.at("/results/1/embedding/0").floatValue()).isEqualTo(0.3f);
        assertThat(j.at("/results/0").size()).isEqualTo(1);
        assertThat(Instant.parse(j.at("/created_at").asText())).isNotNull();
        assertThat(j.at("/input_token_count").asInt()).isEqualTo(2);
    }

    @Test
    void derivesEmbeddingsAndRejectsTooFewVectors() throws Exception {
        JsonNode j = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.derived(4),
                req("{\"model_id\":\"e\",\"inputs\":[\"a\"]}")).body());
        assertThat(j.at("/results/0/embedding")).hasSize(4);

        assertThatThrownBy(() -> d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1f }),
                req("{\"model_id\":\"e\",\"inputs\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void rendersRerankScoresInInputOrder() throws Exception {
        Rendered r = d.render(RERANK, ScoringReply.of(0.2, 0.9, 0.5),
                req("{\"model_id\":\"cross\",\"query\":\"q\",\"inputs\":[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"}]}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/model_id").asText()).isEqualTo("cross");
        assertThat(j.at("/results")).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(j.at("/results/" + i + "/index").asInt()).isEqualTo(i);
        }
        assertThat(j.at("/results/1/score").asDouble()).isEqualTo(0.9);
        assertThat(Instant.parse(j.at("/created_at").asText())).isNotNull();
        assertThat(j.at("/input_token_count").asInt()).isEqualTo(3);
    }

    @Test
    void rerankHonoursTopNAndEqualScores() throws Exception {
        JsonNode top = m.readTree(d.render(RERANK, ScoringReply.of(0.2, 0.9, 0.5),
                req("{\"query\":\"q\",\"inputs\":[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"}],"
                        + "\"parameters\":{\"return_options\":{\"top_n\":2}}}"))
                .body());
        assertThat(top.at("/results")).hasSize(2);
        assertThat(top.at("/results/0/index").asInt()).isEqualTo(1);
        assertThat(top.at("/results/1/index").asInt()).isEqualTo(2);

        JsonNode equal = m.readTree(d.render(RERANK, ScoringReply.equalScores(),
                req("{\"query\":\"q\",\"inputs\":[{\"text\":\"a\"},{\"text\":\"b\"}]}")).body());
        assertThat(equal.at("/results/0/score").asDouble()).isEqualTo(1.0);
        assertThat(equal.at("/results/1/score").asDouble()).isEqualTo(1.0);

        assertThatThrownBy(
                () -> d.render(RERANK, ScoringReply.of(0.1),
                        req("{\"query\":\"q\",\"inputs\":[{\"text\":\"a\"},{\"text\":\"b\"}]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("1 scores").hasMessageContaining("2 inputs");
    }

    @Test
    void rendersOneDetectionPerFlaggedCategory() throws Exception {
        Rendered r = d.render(DETECTION, ModerationReply.flagged("hap", "pii"),
                req("{\"input\":\"you idiot\",\"detectors\":{\"hap\":{},\"pii\":{}}}"));

        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/detections")).hasSize(2);
        JsonNode first = j.at("/detections/0");
        assertThat(first.at("/detection_type").asText()).isEqualTo("hap");
        assertThat(first.at("/detection").asText()).isEqualTo("you idiot");
        assertThat(first.at("/score").asDouble()).isEqualTo(1.0);
        assertThat(first.at("/start").asInt()).isZero();
        assertThat(first.at("/end").asInt()).isEqualTo(9);
        assertThat(j.at("/detections/1/detection_type").asText()).isEqualTo("pii");

        JsonNode clean = m.readTree(d.render(DETECTION, ModerationReply.notFlagged(), req("{\"input\":\"hi\"}")).body());
        assertThat(clean.at("/detections").isArray()).isTrue();
        assertThat(clean.at("/detections")).isEmpty();
    }

    @Test
    void rendersTheDocumentedErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 400, "bad request");

        assertThat(r.status()).isEqualTo(400);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/trace").asText()).isNotBlank();
        assertThat(j.at("/status_code").asInt()).isEqualTo(400);
        assertThat(j.at("/errors")).singleElement().satisfies(e -> {
            assertThat(e.at("/code").asText()).isNotBlank();
            assertThat(e.at("/message").asText()).isEqualTo("bad request");
        });
    }

    @Test
    void gatewayChatIsOpenAiShapedWithTheDocumentedCreatedStatus() throws Exception {
        String body = "{\"model\":\"openai/gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"temperature\":0.5}";
        ChatRequestView v = d.parseChat(GATEWAY_CHAT, json(body));
        assertThat(v.modelName()).isEqualTo("openai/gpt-4o");
        assertThat(v.temperature()).isEqualTo(0.5);
        assertThat(v.messages()).singleElement().satisfies(msg -> assertThat(msg.text()).isEqualTo("hi"));

        Rendered r = d.render(GATEWAY_CHAT, ChatReply.text("Hello").usage(1, 2), req(body));
        assertThat(r.status()).isEqualTo(201);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/object").asText()).isEqualTo("chat.completion");
        assertThat(j.at("/model").asText()).isEqualTo("openai/gpt-4o");
        assertThat(j.at("/choices/0/message/content").asText()).isEqualTo("Hello");
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(3);
    }

    @Test
    void gatewayStreamEndsWithDone() {
        Rendered r = d.render(GATEWAY_STREAM, ChatReply.text("Hi there"),
                req("{\"model\":\"m\",\"stream\":true,\"messages\":[]}"));

        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        assertThat(r.events().get(r.events().size() - 1).data()).isEqualTo("[DONE]");
    }

    @Test
    void gatewayEmbeddingsAreOpenAiShaped() throws Exception {
        Rendered r = d.render(GATEWAY_EMBEDDINGS, EmbeddingReply.derived(3), req("{\"model\":\"e\",\"input\":[\"a\",\"b\"]}"));

        assertThat(r.status()).isEqualTo(201);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/object").asText()).isEqualTo("list");
        assertThat(j.at("/data")).hasSize(2);
        assertThat(j.at("/data/1/index").asInt()).isEqualTo(1);
        assertThat(j.at("/data/1/embedding")).hasSize(3);
    }

    @Test
    void gatewayErrorUsesTheGatewayEnvelope() throws Exception {
        JsonNode j = m.readTree(d.renderError(GATEWAY_CHAT, 429, "slow down").body());

        assertThat(j.at("/error/code").asText()).isEqualTo("429");
        assertThat(j.at("/error/message").asText()).isEqualTo("slow down");
        assertThat(j.has("errors")).isFalse();
    }

    private List<JsonNode> chunks(Rendered r) throws Exception {
        List<JsonNode> chunks = new ArrayList<>();
        for (SseEvent e : r.events()) {
            chunks.add(m.readTree(e.data()));
        }
        return chunks;
    }

    private JsonNode json(String s) {
        try {
            return m.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(CHAT, ScoringReply.equalScores(), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
