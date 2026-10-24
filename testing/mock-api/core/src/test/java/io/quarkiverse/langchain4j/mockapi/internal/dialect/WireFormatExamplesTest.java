package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

/**
 * Renders fixed, provider-neutral replies through every dialect and compares the exchanges with the golden files the
 * user guide includes ({@code docs/modules/ROOT/examples/mock-api/wire/<dialect-id>.txt}). Run with
 * {@code -Dmockapi.wire.update=true} to rewrite the files instead.
 * <p>
 * Each case goes through the same steps as a request to the mock: {@link DialectRegistry#resolve}, the dialect's
 * request view, then {@link Dialect#render} or, for an {@link ErrorReply}, {@link Dialect#renderError}. The values the
 * dialects take from the clock or a random source are replaced, see {@link #normalize}. Response headers other than
 * {@code Content-Type}, such as Bedrock's {@code x-amzn-ErrorType}, are listed after the status line.
 */
class WireFormatExamplesTest {

    private static final Path WIRE_DIR = Path.of("../../../docs/modules/ROOT/examples/mock-api/wire");
    private static final ObjectMapper MAPPER = MockApiJson.mapper();

    private static final Pattern EPOCH_SECONDS = Pattern.compile("(?<key>\"(?:created|created_at)\"\\s*:\\s*)\\d+");
    private static final Pattern ISO_INSTANT = Pattern
            .compile("\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\"");
    private static final Pattern UUID = Pattern
            .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final ChatReply TEXT = ChatReply.text("Hello from the mock").usage(12, 7);
    private static final ChatReply TOOL_CALL = ChatReply.toolCall("get_weather", "{\"city\":\"Athens\"}");
    private static final EmbeddingReply EMBEDDING = EmbeddingReply.of(new float[] { 0.1f, 0.2f, 0.3f });
    private static final ScoringReply SCORES = ScoringReply.of(0.2, 0.9);
    private static final ErrorReply ERROR = ErrorReply.of(429, "slow down");
    private static final DecisionReply DECISION = DecisionReply.of(
            DecisionReply.choice("team", "billing", orderedMap("billing", 0.88, "support", 0.12), 0.81),
            DecisionReply.predicate("urgent", 0.95))
            .usage(318, 34);

    private static final String WEATHER_SCHEMA = """
            {"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}""";
    private static final String WEATHER_DESCRIPTION = "Returns the weather in a city";
    private static final String WEATHER_QUESTION = "What is the weather in Athens?";
    private static final String MODERATED_TEXT = "I am going to hit you.";

    private static final String OPENAI_TOOLS = """
            "tools":[{"type":"function","function":{"name":"get_weather","description":"%s","parameters":%s}}]"""
            .formatted(WEATHER_DESCRIPTION, WEATHER_SCHEMA);

    private static final String GEMINI_CHAT = """
            {"contents":[{"role":"user","parts":[{"text":"Hi"}]}]}""";
    private static final String GEMINI_TOOLS = """
            {"contents":[{"role":"user","parts":[{"text":"%s"}]}],
             "tools":[{"functionDeclarations":[{"name":"get_weather","description":"%s",
               "parameters":{"type":"OBJECT","properties":{"city":{"type":"STRING"}},"required":["city"]}}]}]}"""
            .formatted(WEATHER_QUESTION, WEATHER_DESCRIPTION);
    private static final String GEMINI_MODEL = "/v1beta/models/gemini-2.5-flash";

    private static final String VERTEX_MODELS = "/v1/projects/mock-project/locations/us-central1/publishers/google/models/";

    private static final String WATSONX_VERSION = "version=2025-04-23";

    /**
     * The cases of every dialect, keyed by dialect id, in the order the files list them.
     */
    static Map<String, List<Case>> cases() {
        Map<String, List<Case>> cases = new LinkedHashMap<>();
        cases.put("openai", List.of(
                post("Chat", "/v1/chat/completions", """
                        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"Hi"}]}""", TEXT),
                post("Chat with a tool call", "/v1/chat/completions", """
                        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"%s"}],%s}"""
                        .formatted(WEATHER_QUESTION, OPENAI_TOOLS), TOOL_CALL),
                post("Streaming chat", "/v1/chat/completions", """
                        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"Hi"}],
                         "stream":true,"stream_options":{"include_usage":true}}""", TEXT),
                post("Chat (Responses API)", "/v1/responses", """
                        {"model":"gpt-4o-mini","input":[{"role":"user","content":"Hi"}]}""", TEXT),
                post("Streaming chat (Responses API)", "/v1/responses", """
                        {"model":"gpt-4o-mini","input":[{"role":"user","content":"Hi"}],"stream":true}""", TEXT),
                post("Chat (Azure OpenAI)", "/openai/deployments/mock-deployment/chat/completions", "api-version=2024-10-21",
                        """
                                {"messages":[{"role":"user","content":"Hi"}]}""", TEXT),
                post("Embeddings", "/v1/embeddings", """
                        {"model":"text-embedding-3-small","input":["Hello"]}""", EMBEDDING),
                post("Moderation", "/v1/moderations", """
                        {"model":"omni-moderation-latest","input":["%s"]}""".formatted(MODERATED_TEXT),
                        ModerationReply.flagged("violence")),
                post("Decision", "/v1/decisions",
                        """
                                {"model":"gpt-6-luna","input":"Help! My payouts have been failing for 3 days.",
                                 "questions":[{"type":"choice","name":"team","instructions":"Which team should handle this?",
                                               "choices":[{"value":"billing","description":"Payments"},
                                                          {"value":"support","description":"Bugs"}]},
                                              {"type":"predicate","name":"urgent","instructions":"Does this need attention today?"}]}""",
                        DECISION),
                post("Error", "/v1/chat/completions", """
                        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"Hi"}]}""", ERROR)));
        cases.put("anthropic", List.of(
                post("Chat", "/anthropic/v1/messages", """
                        {"model":"claude-sonnet-4-5","max_tokens":1024,
                         "messages":[{"role":"user","content":[{"type":"text","text":"Hi"}]}]}""", TEXT),
                post("Chat with a tool call", "/anthropic/v1/messages", """
                        {"model":"claude-sonnet-4-5","max_tokens":1024,
                         "messages":[{"role":"user","content":[{"type":"text","text":"%s"}]}],
                         "tools":[{"name":"get_weather","description":"%s","input_schema":%s}]}"""
                        .formatted(WEATHER_QUESTION, WEATHER_DESCRIPTION, WEATHER_SCHEMA), TOOL_CALL),
                post("Streaming chat", "/anthropic/v1/messages", """
                        {"model":"claude-sonnet-4-5","max_tokens":1024,
                         "messages":[{"role":"user","content":[{"type":"text","text":"Hi"}]}],"stream":true}""", TEXT),
                post("Error", "/anthropic/v1/messages", """
                        {"model":"claude-sonnet-4-5","max_tokens":1024,
                         "messages":[{"role":"user","content":[{"type":"text","text":"Hi"}]}]}""", ERROR)));
        cases.put("google-genai", geminiCases("/google-genai",
                post("Embeddings", "/google-genai/v1beta/models/text-embedding-004:batchEmbedContents", """
                        {"requests":[{"model":"models/text-embedding-004","content":{"parts":[{"text":"Hello"}]}}]}""",
                        EMBEDDING)));
        cases.put("ai-gemini", geminiCases("/ai-gemini",
                post("Embeddings", "/ai-gemini/v1beta/models/text-embedding-004:embedContent", """
                        {"model":"models/text-embedding-004","content":{"parts":[{"text":"Hello"}]}}""", EMBEDDING)));
        cases.put("vertexai-gemini", List.of(
                post("Chat", "/vertexai-gemini" + VERTEX_MODELS + "gemini-2.5-flash:generateContent", GEMINI_CHAT, TEXT),
                post("Chat with a tool call", "/vertexai-gemini" + VERTEX_MODELS + "gemini-2.5-flash:generateContent",
                        GEMINI_TOOLS, TOOL_CALL),
                post("Streaming chat", "/vertexai-gemini" + VERTEX_MODELS + "gemini-2.5-flash:streamGenerateContent",
                        "alt=sse", GEMINI_CHAT, TEXT),
                post("Embeddings", "/vertexai-gemini" + VERTEX_MODELS + "text-embedding-004:predict", """
                        {"instances":[{"content":"Hello"}]}""", EMBEDDING),
                post("Error", "/vertexai-gemini" + VERTEX_MODELS + "gemini-2.5-flash:generateContent", GEMINI_CHAT,
                        ERROR)));
        String palmChat = """
                {"instances":[{"context":"","messages":[{"author":"USER","content":"Hi"}]}],
                 "parameters":{"temperature":0.2,"maxOutputTokens":50,"topK":40,"topP":0.9}}""";
        cases.put("vertexai", List.of(
                post("Chat", "/vertexai" + VERTEX_MODELS + "chat-bison:predict", palmChat, TEXT),
                post("Error", "/vertexai" + VERTEX_MODELS + "chat-bison:predict", palmChat, ERROR)));
        cases.put("mistralai", List.of(
                post("Chat", "/mistralai/v1/chat/completions", """
                        {"model":"mistral-small-latest","messages":[{"role":"user","content":"Hi"}]}""", TEXT),
                post("Chat with a tool call", "/mistralai/v1/chat/completions", """
                        {"model":"mistral-small-latest","messages":[{"role":"user","content":"%s"}],%s}"""
                        .formatted(WEATHER_QUESTION, OPENAI_TOOLS), TOOL_CALL),
                post("Streaming chat", "/mistralai/v1/chat/completions", """
                        {"model":"mistral-small-latest","messages":[{"role":"user","content":"Hi"}],"stream":true}""",
                        TEXT),
                post("Embeddings", "/mistralai/v1/embeddings", """
                        {"model":"mistral-embed","input":["Hello"],"encoding_format":"float"}""", EMBEDDING),
                post("Moderation", "/mistralai/v1/moderations", """
                        {"model":"mistral-moderation-latest","input":["%s"]}""".formatted(MODERATED_TEXT),
                        ModerationReply.flagged("violence_and_threats")),
                post("Error", "/mistralai/v1/chat/completions", """
                        {"model":"mistral-small-latest","messages":[{"role":"user","content":"Hi"}]}""", ERROR)));
        cases.put("openshift-ai", List.of(
                post("Chat", "/openshift-ai/api/v1/task/text-generation", """
                        {"model_id":"mock-model","inputs":"Hi"}""", TEXT),
                post("Error", "/openshift-ai/api/v1/task/text-generation", """
                        {"model_id":"mock-model","inputs":"Hi"}""", ERROR)));
        cases.put("ollama", List.of(
                post("Chat", "/ollama/api/chat", """
                        {"model":"llama3.2","messages":[{"role":"user","content":"Hi"}],"stream":false}""", TEXT),
                post("Chat with a tool call", "/ollama/api/chat", """
                        {"model":"llama3.2","messages":[{"role":"user","content":"%s"}],"stream":false,%s}"""
                        .formatted(WEATHER_QUESTION, OPENAI_TOOLS), TOOL_CALL),
                post("Streaming chat", "/ollama/api/chat", """
                        {"model":"llama3.2","messages":[{"role":"user","content":"Hi"}],"stream":true}""", TEXT),
                post("Embeddings", "/ollama/api/embed", """
                        {"model":"nomic-embed-text","input":["Hello"]}""", EMBEDDING),
                post("Error", "/ollama/api/chat", """
                        {"model":"llama3.2","messages":[{"role":"user","content":"Hi"}],"stream":false}""", ERROR)));
        String rerank = """
                {"model":"rerank-v3.5","query":"What is the capital of Greece?",
                 "documents":["Paris is the capital of France.","Athens is the capital of Greece."]}""";
        cases.put("cohere", List.of(
                post("Scoring", "/cohere/v2/rerank", rerank, SCORES),
                post("Error", "/cohere/v2/rerank", rerank, ERROR)));
        String textGeneration = """
                {"inputs":"Hi","parameters":{"return_full_text":false},"options":{"wait_for_model":true}}""";
        cases.put("huggingface", List.of(
                post("Chat", "/huggingface/chat", textGeneration, TEXT),
                post("Embeddings", "/huggingface/embeddings", """
                        {"inputs":["Hello"],"options":{"wait_for_model":true}}""", EMBEDDING),
                post("Error", "/huggingface/chat", textGeneration, ERROR)));
        String systemOne = """
                {"model":"jev-latest","state":{"ticket":"Help! My payouts have been failing for 3 days."},
                 "questions":{
                   "team":{"type":"choice","instructions":"Which team should handle this?",
                           "criteria":{"billing":"Payments, invoicing, refunds","support":"Bugs and outages"}},
                   "urgent":{"type":"noul","instructions":"Does this need attention today?",
                             "criteria":{"true":"Money is not reaching the customer","false":"It can wait"}}}}""";
        cases.put("typesafe", List.of(
                post("Decision", "/typesafe/v1/systemone", systemOne, DECISION),
                post("Error", "/typesafe/v1/systemone", systemOne, ERROR)));
        String watsonxChat = """
                {"model_id":"ibm/granite-3-3-8b-instruct","project_id":"mock-project",
                 "messages":[{"role":"user","content":[{"type":"text","text":"Hi"}]}]}""";
        cases.put("watsonx", List.of(
                post("Chat", "/watsonx/ml/v1/text/chat", WATSONX_VERSION, watsonxChat, TEXT),
                post("Chat with a tool call", "/watsonx/ml/v1/text/chat", WATSONX_VERSION, """
                        {"model_id":"ibm/granite-3-3-8b-instruct","project_id":"mock-project",
                         "messages":[{"role":"user","content":[{"type":"text","text":"%s"}]}],%s}"""
                        .formatted(WEATHER_QUESTION, OPENAI_TOOLS), TOOL_CALL),
                post("Streaming chat", "/watsonx/ml/v1/text/chat_stream", WATSONX_VERSION, watsonxChat, TEXT),
                post("Embeddings", "/watsonx/ml/v1/text/embeddings", WATSONX_VERSION, """
                        {"model_id":"ibm/granite-embedding-278m-multilingual","project_id":"mock-project",
                         "inputs":["Hello"]}""", EMBEDDING),
                post("Moderation", "/watsonx/ml/v1/text/detection", WATSONX_VERSION, """
                        {"input":"%s","detectors":{"hap":{}},"project_id":"mock-project"}""".formatted(MODERATED_TEXT),
                        ModerationReply.flagged("hap")),
                post("Scoring", "/watsonx/ml/v1/text/rerank", WATSONX_VERSION, """
                        {"model_id":"cross-encoder/ms-marco-minilm-l-12-v2","project_id":"mock-project",
                         "query":"What is the capital of Greece?",
                         "inputs":[{"text":"Paris is the capital of France."},{"text":"Athens is the capital of Greece."}]}""",
                        SCORES),
                post("Error", "/watsonx/ml/v1/text/chat", WATSONX_VERSION, watsonxChat, ERROR)));
        String converse = """
                {"messages":[{"role":"user","content":[{"text":"Hi"}]}]}""";
        cases.put("bedrock", List.of(
                post("Chat", "/bedrock/model/us.amazon.nova-lite-v1%3A0/converse", converse, TEXT),
                post("Chat with a tool call", "/bedrock/model/us.amazon.nova-lite-v1%3A0/converse", """
                        {"messages":[{"role":"user","content":[{"text":"%s"}]}],
                         "toolConfig":{"tools":[{"toolSpec":{"name":"get_weather","description":"%s",
                           "inputSchema":{"json":%s}}}]}}"""
                        .formatted(WEATHER_QUESTION, WEATHER_DESCRIPTION, WEATHER_SCHEMA), TOOL_CALL),
                post("Streaming chat", "/bedrock/model/us.amazon.nova-lite-v1%3A0/converse-stream", converse, TEXT),
                post("Embeddings (Titan)", "/bedrock/model/amazon.titan-embed-text-v2%3A0/invoke", """
                        {"inputText":"Hello"}""", EMBEDDING),
                post("Embeddings (Cohere)", "/bedrock/model/cohere.embed-english-v3/invoke", """
                        {"texts":["Hello"],"input_type":"search_document"}""", EMBEDDING),
                post("Error", "/bedrock/model/us.amazon.nova-lite-v1%3A0/converse", converse, ERROR)));
        return cases;
    }

    private static List<Case> geminiCases(String prefix, Case embeddings) {
        return List.of(
                post("Chat", prefix + GEMINI_MODEL + ":generateContent", GEMINI_CHAT, TEXT),
                post("Chat with a tool call", prefix + GEMINI_MODEL + ":generateContent", GEMINI_TOOLS, TOOL_CALL),
                post("Streaming chat", prefix + GEMINI_MODEL + ":streamGenerateContent", "alt=sse", GEMINI_CHAT, TEXT),
                embeddings,
                post("Error", prefix + GEMINI_MODEL + ":generateContent", GEMINI_CHAT, ERROR));
    }

    static Stream<Arguments> dialects() {
        return cases().entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @Test
    void everyDialectHasExamples() {
        assertThat(cases().keySet()).containsExactlyInAnyOrderElementsOf(
                DialectRegistry.load().dialects().stream().map(Dialect::id).toList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void matchesGoldenFile(String dialectId, List<Case> cases) throws IOException {
        DialectRegistry registry = DialectRegistry.load();
        List<String> rendered = new ArrayList<>();
        for (Case c : cases) {
            rendered.add(render(registry, dialectId, c));
        }
        String actual = normalize(String.join("\n", rendered));
        Path file = WIRE_DIR.resolve(dialectId + ".txt");
        if (Boolean.getBoolean("mockapi.wire.update")) {
            Files.createDirectories(WIRE_DIR);
            Files.writeString(file, actual);
            return;
        }
        assertThat(file).as("missing %s: run with -Dmockapi.wire.update=true to generate it", file).exists();
        assertThat(actual).as("%s is out of date: run with -Dmockapi.wire.update=true to regenerate it", file)
                .isEqualTo(Files.readString(file));
    }

    private static String render(DialectRegistry registry, String dialectId, Case c) throws IOException {
        JsonNode json = MAPPER.readTree(c.body());
        DialectRegistry.Resolved resolved = registry.resolve(c.method(), c.path(), parseQuery(c.query()), json)
                .orElseThrow(() -> new AssertionError("no dialect matches " + dialectId + " / " + c.title()));
        Dialect dialect = resolved.dialect();
        Dialect.Match match = resolved.match();
        assertThat(dialect.id()).as(c.title()).isEqualTo(dialectId);

        ChatRequestView chat = match.operation() == Operation.CHAT ? dialect.parseChat(match, json) : null;
        DecisionRequestView decision = match.operation() == Operation.DECISION ? dialect.parseDecision(match, json)
                : null;
        CapturedRequest request = new CapturedRequestImpl(1, c.method(), c.path(), c.query(), Map.of(), c.body(),
                match.operation(), dialect.id(), chat, decision);
        Rendered response;
        if (c.reply() instanceof ErrorReply error) {
            response = dialect.renderError(match, error.status(), error.message());
        } else {
            response = dialect.render(match, c.reply(), request);
            assertThat(response.status()).as("%s / %s: %s", dialectId, c.title(), body(response)).isBetween(200, 299);
        }

        StringBuilder out = new StringBuilder();
        out.append("=== ").append(c.title()).append('\n');
        out.append("--> ").append(c.method()).append(' ').append(c.path());
        if (c.query() != null) {
            out.append('?').append(c.query());
        }
        out.append('\n').append(pretty(c.body().getBytes(StandardCharsets.UTF_8))).append('\n');
        out.append("<-- ").append(response.status()).append(" [").append(contentType(response)).append("]\n");
        new TreeMap<>(response.headers()).forEach((name, value) -> {
            if (!name.equalsIgnoreCase("Content-Type")) {
                out.append(name).append(": ").append(value).append('\n');
            }
        });
        out.append(body(response));
        return out.toString().stripTrailing() + "\n";
    }

    /**
     * The response body as the mock writes it, with a JSON body pretty printed and each stream event framed as
     * {@code ResponseWriter.write} frames it; an AWS event stream message, which is binary, is shown as its
     * {@code :event-type} header and its JSON payload.
     */
    private static String body(Rendered response) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (response.streamFormat() == Rendered.StreamFormat.NONE) {
            if (response.body() != null) {
                sb.append(pretty(response.body())).append('\n');
            }
        } else if (response.streamFormat() == Rendered.StreamFormat.AWS_EVENT_STREAM) {
            for (SseEvent e : response.events()) {
                sb.append("event-type: ").append(e.event()).append(' ').append(e.data()).append('\n');
            }
        } else {
            for (SseEvent e : response.events()) {
                sb.append(new String(response.streamFormat().frame(e), StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    // as in ResponseWriter.write, which sets the Content-Type of the stream formats itself
    private static String contentType(Rendered response) {
        return response.streamFormat() == Rendered.StreamFormat.NONE ? response.headers().get("Content-Type")
                : response.streamFormat().contentType();
    }

    private static String pretty(byte[] json) throws IOException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(json));
    }

    /**
     * Replaces what the dialects take from the clock or a random source: {@code created}/{@code created_at} epoch
     * seconds (OpenAI, Mistral, watsonx) become {@code 1767225600}, ISO-8601 instants (Ollama and watsonx
     * {@code created_at}) become {@code "2026-01-01T00:00:00Z"}, and random UUIDs (the Cohere rerank {@code id}, the
     * watsonx error {@code trace}) become the nil UUID. The pretty printer's line separator is the platform's, so it is
     * made {@code \n}.
     */
    private static String normalize(String text) {
        String result = EPOCH_SECONDS.matcher(text).replaceAll("${key}1767225600");
        result = ISO_INSTANT.matcher(result).replaceAll("\"2026-01-01T00:00:00Z\"");
        result = UUID.matcher(result).replaceAll("00000000-0000-0000-0000-000000000000");
        return result.replace("\r\n", "\n");
    }

    private static Map<String, List<String>> parseQuery(String query) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                result.computeIfAbsent(pair.substring(0, eq), k -> new ArrayList<>()).add(pair.substring(eq + 1));
            }
        }
        return result;
    }

    private static Map<String, Double> orderedMap(String k1, double v1, String k2, double v2) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static Case post(String title, String path, String body, Reply reply) {
        return new Case(title, "POST", path, null, body, reply);
    }

    private static Case post(String title, String path, String query, String body, Reply reply) {
        return new Case(title, "POST", path, query, body, reply);
    }

    /**
     * One exchange: the request as the provider's client sends it, with the path including the dialect's path prefix
     * and the query (or null), and the reply the mock is scripted with.
     */
    record Case(String title, String method, String path, String query, String body, Reply reply) {

        @Override
        public String toString() {
            return title;
        }
    }
}
