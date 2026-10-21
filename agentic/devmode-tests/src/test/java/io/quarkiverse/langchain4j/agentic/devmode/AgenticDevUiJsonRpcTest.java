package io.quarkiverse.langchain4j.agentic.devmode;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.agentic.devmode.AgenticDevUiApp.HelpDesk;
import io.quarkiverse.langchain4j.agentic.devmode.AgenticDevUiApp.MonitoredDesk;
import io.quarkiverse.langchain4j.agentic.devmode.AgenticDevUiApp.RetryingDesk;
import io.quarkiverse.langchain4j.agentic.devmode.AgenticDevUiApp.StoryWorkflow;
import io.quarkiverse.langchain4j.agentic.devmode.AgenticDevUiApp.SupportDesk;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Exercises the agentic Dev UI JSON-RPC service in dev mode: the {@code invokeAgent} allow-list, the topology and
 * execution JSON carrying everything the LangChain4j {@code HtmlReportGenerator} renders, and the scoping of
 * executions to the root agent that started them.
 */
public class AgenticDevUiJsonRpcTest extends DevUIJsonRPCTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(new TestApplication())
            .setLogRecordPredicate(new ListenerFailures());

    public AgenticDevUiJsonRpcTest() {
        super("quarkus-langchain4j-agentic");
    }

    @Test
    void invokeAgentRejectsNonAgentClass() throws Exception {
        JsonNode response = executeJsonRPCMethod("invokeAgent", Map.of(
                "agentClassName", "java.lang.String", "methodName", "toString", "inputJson", "{}"));
        assertThat(response.get("success").asBoolean()).isFalse();
        assertThat(response.get("error").asText()).contains("Unknown agent class");
    }

    @Test
    void listsEveryRootAgent() throws Exception {
        JsonNode roots = executeJsonRPCMethod("getRootAgentEntries");
        List<String> names = new ArrayList<>();
        for (JsonNode root : roots) {
            names.add(root.get("typeName").asText());
        }
        assertThat(names).containsExactly("HelpDesk", "MonitoredDesk", "RetryingDesk", "StoryWorkflow", "SupportDesk");
    }

    @Test
    void topologyCarriesEveryFieldOfTheReport() throws Exception {
        JsonNode topology = executeJsonRPCMethod("getTopology", Map.of("index", rootIndex("StoryWorkflow")));

        assertThat(texts(topology.get("legend")))
                .containsExactly("SEQUENCE", "AI_AGENT", "LOOP", "PARALLEL", "NON_AI_AGENT", "ROUTER");
        JsonNode firstKey = topology.get("dataFlow").get(0);
        assertThat(firstKey.get("key").asText()).isEqualTo("story");
        assertThat(firstKey.get("color").asText()).isEqualTo("#e63946");

        JsonNode root = topology.get("root");
        assertThat(root.get("name").asText()).isEqualTo("write");
        assertThat(root.get("topology").asText()).isEqualTo("SEQUENCE");
        assertThat(root.get("typeName").asText()).isEqualTo("StoryWorkflow");
        assertThat(root.get("agentId").asText()).isNotBlank();
        assertThat(root.get("description").asText()).isEqualTo("Writes, reviews and publishes a story");
        assertThat(root.get("outputType").asText()).isEqualTo("String");
        assertThat(root.get("outputKey").asText()).isEqualTo("story");
        assertThat(root.get("plannerType").asText()).isNotBlank();
        assertThat(argumentNames(root)).contains("topic", "style", "audience");
        assertThat(names(root.get("subagents"))).containsExactly("generateStory", "review", "reviewAll", "publish");

        JsonNode loop = child(root, "review");
        assertThat(loop.get("topology").asText()).isEqualTo("LOOP");
        assertThat(loop.get("loop").get("maxIterations").asInt()).isEqualTo(3);
        assertThat(loop.get("loop").get("exitCondition").asText()).isEqualTo("score >= 0.8");
        assertThat(loop.get("loop").get("testExitAtLoopEnd").asBoolean()).isTrue();
        assertThat(names(loop.get("subagents"))).containsExactly("scoreStyle", "editStory");

        JsonNode parallel = child(root, "reviewAll");
        assertThat(parallel.get("topology").asText()).isEqualTo("PARALLEL");
        JsonNode audience = child(parallel, "reviewAudience");
        assertThat(audience.get("async").asBoolean()).isTrue();
        assertThat(audience.get("outputKey").asText()).isEqualTo("audienceReview");
        JsonNode length = child(parallel, "checkLength");
        assertThat(length.get("topology").asText()).isEqualTo("NON_AI_AGENT");
        assertThat(length.get("outputType").asText()).isEqualTo("int");

        JsonNode router = child(root, "publish");
        assertThat(router.get("topology").asText()).isEqualTo("ROUTER");
        assertThat(child(router, "publishBlog").get("condition").asText()).isEqualTo("wordCount > 10");
        assertThat(child(router, "publishNewsletter").get("condition").asText()).isEqualTo("wordCount <= 10");

        String report = executeJsonRPCMethod(String.class, "getTopologyReportHtml",
                Map.of("index", rootIndex("StoryWorkflow")));
        List<String> nodeNames = new ArrayList<>();
        collectNames(root, nodeNames);
        assertThat(occurrences(report, "class=\"node-card ")).isEqualTo(nodeNames.size());
        for (String name : nodeNames) {
            assertThat(report).contains("<span class=\"agent-name\">" + name + "</span>");
        }
        assertThat(report).contains("when: wordCount &gt; 10", "exit: score &gt;= 0.8", "test at end", "async");
    }

    @Test
    void executionsCarryEveryFieldOfTheReport() throws Exception {
        int index = rootIndex("StoryWorkflow");
        invoke(StoryWorkflow.class, "write",
                "{\"session\":\"exec-ok\",\"topic\":\"dragons\",\"style\":\"fantasy\",\"audience\":\"kids\"}", true);
        invoke(StoryWorkflow.class, "write",
                "{\"session\":\"exec-fail\",\"topic\":\"fail\",\"style\":\"fantasy\",\"audience\":\"kids\"}", false);

        JsonNode report = executeJsonRPCMethod("getExecutions", Map.of("index", index));
        JsonNode ok = session(report, "exec-ok");
        assertThat(ok.get("runs").asInt()).isEqualTo(1);
        assertThat(ok.get("success").asInt()).isEqualTo(1);
        JsonNode execution = ok.get("executions").get(0);
        assertThat(execution.get("status").asText()).isEqualTo("ok");

        JsonNode top = execution.get("topLevel");
        assertThat(top.get("name").asText()).isEqualTo("write");
        assertThat(top.get("topology").asText()).isEqualTo("SEQUENCE");
        assertThat(top.get("done").asBoolean()).isTrue();
        assertThat(top.get("durationMillis").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(top.get("startTime").asText()).matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
        assertThat(top.get("finishTime").asText()).matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
        assertThat(top.get("inputs").get("topic").asText()).isEqualTo("dragons");
        assertThat(top.get("output").asText()).startsWith("Edited:");
        assertThat(names(top.get("nestedInvocations"))).containsExactly("generateStory", "review", "reviewAll", "publish");

        JsonNode writer = child(top, "generateStory");
        assertThat(writer.get("totalTokenCount").asInt()).isPositive();
        JsonNode tool = writer.get("toolExecutions").get(0);
        assertThat(tool.get("name").asText()).isEqualTo("countWords");
        assertThat(tool.get("arguments").asText()).contains("dragons");
        assertThat(tool.get("result").asText()).isEqualTo("1");
        assertThat(tool.get("failed").asBoolean()).isFalse();
        assertThat(tool.get("durationMillis").asLong()).isGreaterThanOrEqualTo(0);

        JsonNode loop = child(top, "review");
        List<Integer> iterations = new ArrayList<>();
        for (JsonNode nested : loop.get("nestedInvocations")) {
            iterations.add(nested.get("iterationIndex").asInt());
        }
        assertThat(iterations).containsExactly(0, 0, 1, 1);
        assertThat(names(child(top, "reviewAll").get("nestedInvocations"))).contains("checkLength");
        assertThat(names(child(top, "publish").get("nestedInvocations"))).containsExactly("publishBlog");

        JsonNode failed = session(report, "exec-fail");
        assertThat(failed.get("failed").asInt()).isEqualTo(1);
        JsonNode failure = failed.get("executions").get(0);
        assertThat(failure.get("status").asText()).isEqualTo("fail");
        assertThat(failure.get("error").get("agentName").asText()).isEqualTo("editStory");
        assertThat(failure.get("error").get("message").asText()).contains("Failed to invoke agent method");
        assertThat(failure.get("error").get("rootCause").asText())
                .isEqualTo("IllegalStateException: Style editor rejected the draft");

        String html = executeJsonRPCMethod(String.class, "getExecutionReportHtml", Map.of("index", index));
        assertThat(html).contains("Session: exec-ok", "Session: exec-fail", "Error in editStory");
        assertThat(matches(html, "<tr[^>]* data-row-id=\"\\d+\"")).isEqualTo(rowCount(report));
        assertThat(devMode.getLogRecords()).isEmpty();
    }

    @Test
    void executionsAreScopedToTheRootThatStartedThem() throws Exception {
        invoke(SupportDesk.class, "ask", "{\"session\":\"support-only\",\"topic\":\"refunds\"}", true);
        invoke(HelpDesk.class, "help", "{\"session\":\"help-only\",\"topic\":\"returns\"}", true);

        List<String> supportSessions = memoryIds(executeJsonRPCMethod("getExecutions",
                Map.of("index", rootIndex("SupportDesk"))));
        List<String> helpSessions = memoryIds(executeJsonRPCMethod("getExecutions",
                Map.of("index", rootIndex("HelpDesk"))));
        List<String> storySessions = memoryIds(executeJsonRPCMethod("getExecutions",
                Map.of("index", rootIndex("StoryWorkflow"))));

        assertThat(supportSessions).contains("support-only").doesNotContain("help-only");
        assertThat(helpSessions).contains("help-only").doesNotContain("support-only");
        assertThat(storySessions).doesNotContain("support-only", "help-only");
        assertThat(devMode.getLogRecords()).isEmpty();
    }

    @Test
    void monitoredAgentRootReusesItsOwnMonitor() throws Exception {
        invoke(MonitoredDesk.class, "greet", "{\"session\":\"own-monitor\",\"topic\":\"hello\"}", true);

        given().port(HTTP_PORT).when().get("/monitor/own-monitor-sessions").then().statusCode(200)
                .body(containsString("own-monitor"));
        JsonNode session = session(executeJsonRPCMethod("getExecutions", Map.of("index", rootIndex("MonitoredDesk"))),
                "own-monitor");
        assertThat(names(session.get("executions").get(0).get("topLevel").get("nestedInvocations")))
                .containsExactly("sayHello");
        assertThat(devMode.getLogRecords()).isEmpty();
    }

    @Test
    void executionsVersionChangesOnlyWhenTheExecutionsChange() throws Exception {
        int index = rootIndex("SupportDesk");
        String before = executeJsonRPCMethod(String.class, "getExecutionsVersion", Map.of("index", index));
        assertThat(before).startsWith(SupportDesk.class.getName() + "|");
        assertThat(executeJsonRPCMethod(String.class, "getExecutionsVersion", Map.of("index", index))).isEqualTo(before);

        invoke(SupportDesk.class, "ask", "{\"session\":\"versioned\",\"topic\":\"refunds\"}", true);

        String after = executeJsonRPCMethod(String.class, "getExecutionsVersion", Map.of("index", index));
        assertThat(after).startsWith(SupportDesk.class.getName() + "|").isNotEqualTo(before);
        assertThat(executeJsonRPCMethod(String.class, "getExecutionsVersion", Map.of("index", rootIndex("HelpDesk"))))
                .startsWith(HelpDesk.class.getName() + "|");
    }

    @Test
    void executionRecoveredByTheErrorHandlerIsNotReportedAsFailed() throws Exception {
        invoke(RetryingDesk.class, "retry", "{\"session\":\"retried\",\"topic\":\"rain\"}", true);

        JsonNode session = session(executeJsonRPCMethod("getExecutions", Map.of("index", rootIndex("RetryingDesk"))),
                "retried");
        assertThat(session.get("failed").asInt()).isZero();
        assertThat(session.get("completedWithError").asInt()).isEqualTo(1);
        JsonNode execution = session.get("executions").get(0);
        assertThat(execution.get("status").asText()).isEqualTo("warn");
        assertThat(execution.get("error").get("agentName").asText()).isEqualTo("flaky");
        assertThat(execution.get("error").get("rootCause").asText())
                .isEqualTo("IllegalStateException: Transient failure for rain");
        JsonNode top = execution.get("topLevel");
        assertThat(top.get("done").asBoolean()).isTrue();
        List<Boolean> attempts = new ArrayList<>();
        for (JsonNode attempt : top.get("nestedInvocations")) {
            attempts.add(attempt.get("done").asBoolean());
        }
        assertThat(attempts).containsExactly(false, true);
        assertThat(devMode.getLogRecords()).isEmpty();
    }

    private void invoke(Class<?> agent, String method, String inputJson, boolean expectSuccess) throws Exception {
        JsonNode response = executeJsonRPCMethod("invokeAgent", Map.of(
                "agentClassName", agent.getName(), "methodName", method, "inputJson", inputJson));
        assertThat(response.get("success").asBoolean()).as(response.toString()).isEqualTo(expectSuccess);
    }

    private int rootIndex(String typeName) throws Exception {
        for (JsonNode root : executeJsonRPCMethod("getRootAgentEntries")) {
            if (typeName.equals(root.get("typeName").asText())) {
                return root.get("index").asInt();
            }
        }
        throw new AssertionError("Root agent not found: " + typeName);
    }

    private static JsonNode session(JsonNode report, String memoryId) {
        for (JsonNode session : report.get("sessions")) {
            if (memoryId.equals(session.get("memoryId").asText())) {
                return session;
            }
        }
        throw new AssertionError("Session not found: " + memoryId + " in " + report);
    }

    private static List<String> memoryIds(JsonNode report) {
        List<String> ids = new ArrayList<>();
        for (JsonNode session : report.get("sessions")) {
            ids.add(session.get("memoryId").asText());
        }
        return ids;
    }

    private static JsonNode child(JsonNode node, String name) {
        JsonNode children = node.has("subagents") ? node.get("subagents") : node.get("nestedInvocations");
        for (JsonNode child : children) {
            if (name.equals(child.get("name").asText())) {
                return child;
            }
        }
        throw new AssertionError("Child not found: " + name);
    }

    private static List<String> names(JsonNode nodes) {
        List<String> names = new ArrayList<>();
        for (JsonNode node : nodes) {
            names.add(node.get("name").asText());
        }
        return names;
    }

    private static List<String> texts(JsonNode nodes) {
        List<String> texts = new ArrayList<>();
        for (JsonNode node : nodes) {
            texts.add(node.asText());
        }
        return texts;
    }

    private static List<String> argumentNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (JsonNode argument : node.get("arguments")) {
            names.add(argument.get("name").asText());
        }
        return names;
    }

    private static void collectNames(JsonNode node, List<String> names) {
        names.add(node.get("name").asText());
        for (JsonNode child : node.get("subagents")) {
            collectNames(child, names);
        }
    }

    private static int rowCount(JsonNode report) {
        int rows = 0;
        for (JsonNode session : report.get("sessions")) {
            for (JsonNode execution : session.get("executions")) {
                JsonNode top = execution.get("topLevel");
                if (top.get("done").asBoolean() || !top.get("nestedInvocations").isEmpty()
                        || !top.get("toolExecutions").isEmpty()) {
                    rows += invocationRows(top);
                }
            }
        }
        return rows;
    }

    private static int invocationRows(JsonNode invocation) {
        int rows = 1 + invocation.get("toolExecutions").size();
        for (JsonNode nested : invocation.get("nestedInvocations")) {
            rows += invocationRows(nested);
        }
        return rows;
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int from = text.indexOf(token);
        while (from >= 0) {
            count++;
            from = text.indexOf(token, from + token.length());
        }
        return count;
    }

    private static int matches(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find an available HTTP port for dev mode test", e);
        }
    }

    public static class TestApplication implements Supplier<JavaArchive> {

        @Override
        public JavaArchive get() {
            return ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ScriptedChatModel.class, AgenticDevUiApp.class)
                    .addClasses(AgenticDevUiApp.class.getDeclaredClasses())
                    .addAsResource(new StringAsset(String.join("\n",
                            "quarkus.http.port=" + HTTP_PORT,
                            "quarkus.langchain4j.openai.api-key=offline",
                            "quarkus.langchain4j.openai.base-url=http://localhost:" + HTTP_PORT + "/v1")),
                            "application.properties");
        }
    }

    /**
     * Captures the errors LangChain4j logs when an agent listener, such as an {@code AgentMonitor}, throws.
     */
    public static class ListenerFailures implements Predicate<LogRecord> {

        @Override
        public boolean test(LogRecord record) {
            return record.getLevel().intValue() >= Level.SEVERE.intValue()
                    && record.getLoggerName() != null
                    && record.getLoggerName().startsWith("dev.langchain4j.agentic");
        }
    }
}
