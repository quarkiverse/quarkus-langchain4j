package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Real-world scenario tests for tool guardrails (PII filtering, rate limiting, authentication,
 * input sanitization, output size limits, and combined guardrails)
 */
public class MoreRealWorldToolGuardrailsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            PiiFilterGuardrail.class,
                            RateLimitGuardrail.class,
                            AuthenticationGuardrail.class,
                            InputSanitizationGuardrail.class,
                            OutputSizeLimitGuardrail.class,
                            SqlInjectionGuardrail.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        PiiFilterGuardrail.reset();
        RateLimitGuardrail.reset();
        AuthenticationGuardrail.reset();
        InputSanitizationGuardrail.reset();
        OutputSizeLimitGuardrail.reset();
        SqlInjectionGuardrail.reset();
        tools.reset();
    }

    @Test
    @ActivateRequestContext
    void testPiiFilter_redactsSsn() {
        String result = aiService.chat("test", "searchUser - John SSN:123-45-6789");

        // Tool should have executed with original input
        assertThat(tools.searchUserExecuted).isTrue();
        assertThat(tools.lastInput).contains("123-45-6789");

        // Output should have SSN redacted
        assertThat(result).contains("SSN:***-**-****");
        assertThat(result).doesNotContain("123-45-6789");

        // PII filter should have been executed
        assertThat(PiiFilterGuardrail.executionCount).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testPiiFilter_redactsCreditCard() {
        String result = aiService.chat("test", "searchUser - Card:4532-1111-2222-3333");

        // Output should have credit card redacted
        assertThat(result).contains("Card:****-****-****-****");
        assertThat(result).doesNotContain("4532-1111-2222-3333");
    }

    @Test
    @ActivateRequestContext
    void testPiiFilter_redactsEmail() {
        String result = aiService.chat("test", "searchUser - Contact:john.doe@example.com");

        // Output should have email redacted
        assertThat(result).contains("Contact:***@***");
        assertThat(result).doesNotContain("john.doe@example.com");
    }

    @Test
    @ActivateRequestContext
    void testPiiFilter_redactsMultiplePiiTypes() {
        String result = aiService.chat("test",
                "searchUser - John SSN:123-45-6789 Email:john@example.com Card:4532-1111-2222-3333");

        // All PII should be redacted
        assertThat(result).contains("SSN:***-**-****");
        assertThat(result).contains("Email:***@***");
        assertThat(result).contains("Card:****-****-****-****");

        // Original values should not be present
        assertThat(result).doesNotContain("123-45-6789");
        assertThat(result).doesNotContain("john@example.com");
        assertThat(result).doesNotContain("4532-1111-2222-3333");
    }

    @Test
    @ActivateRequestContext
    void testRateLimit_allowsWithinLimit() {
        // First call should succeed
        String result = aiService.chat("user1", "rateLimitedSearch - query1");
        assertThat(tools.rateLimitedSearchExecuted).isTrue();
        assertThat(result).contains("Search: query1");

        tools.reset();

        // Second call should also succeed (within limit)
        result = aiService.chat("user1", "rateLimitedSearch - query2");
        assertThat(tools.rateLimitedSearchExecuted).isTrue();
        assertThat(result).contains("Search: query2");
    }

    @Test
    @ActivateRequestContext
    void testRateLimit_blocksWhenExceeded() {
        // Exhaust the rate limit (3 calls per user)
        aiService.chat("user2", "rateLimitedSearch - query1");
        tools.reset();
        aiService.chat("user2", "rateLimitedSearch - query2");
        tools.reset();
        aiService.chat("user2", "rateLimitedSearch - query3");
        tools.reset();

        // Fourth call should be rate limited
        String result = aiService.chat("user2", "rateLimitedSearch - query4");

        // Tool should NOT have been executed
        assertThat(tools.rateLimitedSearchExecuted).isFalse();

        // Result should contain rate limit message
        assertThat(result).contains("Rate limit exceeded");
    }

    @Test
    @ActivateRequestContext
    void testRateLimit_separatePerUser() {
        // User1 makes calls
        aiService.chat("user3", "rateLimitedSearch - query1");
        aiService.chat("user3", "rateLimitedSearch - query2");
        aiService.chat("user3", "rateLimitedSearch - query3");

        // User2 should still be able to make calls (separate quota)
        tools.reset();
        String result = aiService.chat("user4", "rateLimitedSearch - query1");

        assertThat(tools.rateLimitedSearchExecuted).isTrue();
        assertThat(result).contains("Search: query1");
    }

    @Test
    @ActivateRequestContext
    void testAuthentication_allowsAuthenticatedUser() {
        // Set authenticated user
        AuthenticationGuardrail.setCurrentUser("admin");

        String result = aiService.chat("test", "secureOperation - data");

        // Tool should have executed
        assertThat(tools.secureOperationExecuted).isTrue();
        assertThat(result).contains("Operation: data");
    }

    @Test
    @ActivateRequestContext
    void testAuthentication_blocksUnauthenticatedUser() {
        // No user authenticated
        AuthenticationGuardrail.setCurrentUser(null);

        assertThatThrownBy(() -> aiService.chat("test", "secureOperation - data"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Authentication required");

        // Tool should NOT have been executed
        assertThat(tools.secureOperationExecuted).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testAuthentication_blocksUnauthorizedUser() {
        // Set user without required role
        AuthenticationGuardrail.setCurrentUser("guest");

        assertThatThrownBy(() -> aiService.chat("test", "secureOperation - data"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Insufficient permissions");

        // Tool should NOT have been executed
        assertThat(tools.secureOperationExecuted).isFalse();
    }

    @Test
    @ActivateRequestContext
    void testInputSanitization_allowsCleanInput() {
        String result = aiService.chat("test", "processInput - Hello World");

        // Tool should have executed
        assertThat(tools.processInputExecuted).isTrue();
        assertThat(result).contains("Processed: Hello World");
    }

    @Test
    @ActivateRequestContext
    void testInputSanitization_blocksXssAttempt() {
        String result = aiService.chat("test", "processInput - <script>alert('xss')</script>");

        // Tool should NOT have been executed
        assertThat(tools.processInputExecuted).isFalse();

        // Result should contain sanitization error
        assertThat(result).contains("Input validation failed");
        assertThat(result).contains("potentially malicious content");
    }

    @Test
    @ActivateRequestContext
    void testInputSanitization_blocksSqlInjection() {
        String result = aiService.chat("test", "databaseQuery - SELECT * FROM users WHERE id=1 OR 1=1");

        // Tool should NOT have been executed
        assertThat(tools.databaseQueryExecuted).isFalse();

        // Result should contain SQL injection warning
        assertThat(result).contains("SQL injection attempt detected");
    }

    @Test
    @ActivateRequestContext
    void testInputSanitization_allowsParameterizedQuery() {
        String result = aiService.chat("test", "databaseQuery - user_id:12345");

        // Tool should have executed (safe parameterized format)
        assertThat(tools.databaseQueryExecuted).isTrue();
        assertThat(result).contains("Query result for: user_id:12345");
    }

    @Test
    @ActivateRequestContext
    void testOutputSizeLimit_allowsNormalOutput() {
        String result = aiService.chat("test", "generateReport - short");

        // Tool should have executed
        assertThat(tools.generateReportExecuted).isTrue();
        assertThat(result).contains("Report: short data");
    }

    @Test
    @ActivateRequestContext
    void testOutputSizeLimit_truncatesLargeOutput() {
        String result = aiService.chat("test", "generateReport - large");

        // Tool should have executed
        assertThat(tools.generateReportExecuted).isTrue();

        // Output should be truncated
        assertThat(result).contains("[Output truncated");
        assertThat(result).contains("exceeded maximum size");

        // Original large output should not be fully present
        assertThat(result.length()).isLessThan(10000); // Original was 15000 chars
    }

    @Test
    @ActivateRequestContext
    void testCombinedGuardrails_allPass() {
        // Set up authentication
        AuthenticationGuardrail.setCurrentUser("admin");

        String result = aiService.chat("user5", "secureSearch - John Doe");

        // All guardrails should pass, tool should execute
        assertThat(tools.secureSearchExecuted).isTrue();

        // Output should have PII filtered
        assertThat(AuthenticationGuardrail.executionCount).isEqualTo(1);
        assertThat(RateLimitGuardrail.executionCount).isEqualTo(1);
        assertThat(PiiFilterGuardrail.executionCount).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void testCombinedGuardrails_authenticationFailsFirst() {
        // No authentication
        AuthenticationGuardrail.setCurrentUser(null);

        assertThatThrownBy(() -> aiService.chat("user6", "secureSearch - query"))
                .isInstanceOf(ToolGuardrailException.class)
                .hasMessageContaining("Authentication required");

        // Tool should NOT have been executed
        assertThat(tools.secureSearchExecuted).isFalse();

        // Authentication failed, so rate limit should not be checked
        assertThat(AuthenticationGuardrail.executionCount).isEqualTo(1);
        assertThat(RateLimitGuardrail.executionCount).isEqualTo(0);
    }

    @Test
    @ActivateRequestContext
    void testCombinedGuardrails_rateLimitFailsSecond() {
        // Set up authentication
        AuthenticationGuardrail.setCurrentUser("admin");

        // Exhaust rate limit
        aiService.chat("user7", "secureSearch - query1");
        aiService.chat("user7", "secureSearch - query2");
        aiService.chat("user7", "secureSearch - query3");

        tools.reset();

        // Next call should be rate limited
        String result = aiService.chat("user7", "secureSearch - query4");

        // Tool should NOT have been executed
        assertThat(tools.secureSearchExecuted).isFalse();

        // Result should contain rate limit message
        assertThat(result).contains("Rate limit exceeded");

        // Auth passed, rate limit checked, PII filter not reached
        assertThat(AuthenticationGuardrail.executionCount).isEqualTo(4);
        assertThat(RateLimitGuardrail.executionCount).isEqualTo(4);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Singleton
    public static class MyTools {
        boolean searchUserExecuted = false;
        boolean rateLimitedSearchExecuted = false;
        boolean secureOperationExecuted = false;
        boolean processInputExecuted = false;
        boolean databaseQueryExecuted = false;
        boolean generateReportExecuted = false;
        boolean secureSearchExecuted = false;
        String lastInput = null;

        @Tool
        @ToolOutputGuardrails({ PiiFilterGuardrail.class })
        public String searchUser(String query) {
            searchUserExecuted = true;
            lastInput = query;
            // Return result that might contain PII
            return "User found: " + query;
        }

        @Tool
        @ToolInputGuardrails({ RateLimitGuardrail.class })
        public String rateLimitedSearch(String query) {
            rateLimitedSearchExecuted = true;
            lastInput = query;
            return "Search: " + query;
        }

        @Tool
        @ToolInputGuardrails({ AuthenticationGuardrail.class })
        public String secureOperation(String data) {
            secureOperationExecuted = true;
            lastInput = data;
            return "Operation: " + data;
        }

        @Tool
        @ToolInputGuardrails({ InputSanitizationGuardrail.class })
        public String processInput(String input) {
            processInputExecuted = true;
            lastInput = input;
            return "Processed: " + input;
        }

        @Tool
        @ToolInputGuardrails({ SqlInjectionGuardrail.class })
        public String databaseQuery(String query) {
            databaseQueryExecuted = true;
            lastInput = query;
            return "Query result for: " + query;
        }

        @Tool
        @ToolOutputGuardrails({ OutputSizeLimitGuardrail.class })
        public String generateReport(String type) {
            generateReportExecuted = true;
            lastInput = type;

            if ("short".equals(type)) {
                return "Report: short data";
            } else if ("large".equals(type)) {
                // Generate a large output (15000 characters total - triggers truncation)
                return "Report: " + "x".repeat(14992);
            }

            return "Report: " + type;
        }

        @Tool
        @ToolInputGuardrails({ AuthenticationGuardrail.class, RateLimitGuardrail.class })
        @ToolOutputGuardrails({ PiiFilterGuardrail.class })
        public String secureSearch(String query) {
            secureSearchExecuted = true;
            lastInput = query;
            return "Secure result: " + query;
        }

        void reset() {
            searchUserExecuted = false;
            rateLimitedSearchExecuted = false;
            secureOperationExecuted = false;
            processInputExecuted = false;
            databaseQueryExecuted = false;
            generateReportExecuted = false;
            secureSearchExecuted = false;
            lastInput = null;
        }
    }

    @ApplicationScoped
    public static class PiiFilterGuardrail implements ToolOutputGuardrail {
        static int executionCount = 0;

        // PII patterns
        private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
        private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}");
        private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;

            String resultText = request.resultText();
            String filtered = resultText;

            // Redact SSN
            filtered = SSN_PATTERN.matcher(filtered).replaceAll("***-**-****");

            // Redact credit cards
            filtered = CREDIT_CARD_PATTERN.matcher(filtered).replaceAll("****-****-****-****");

            // Redact emails
            filtered = EMAIL_PATTERN.matcher(filtered).replaceAll("***@***");

            // If anything was redacted, return modified result
            if (!filtered.equals(resultText)) {
                ToolExecutionResult modifiedResult = ToolExecutionResult.builder()
                        .resultText(filtered)
                        .build();
                return ToolOutputGuardrailResult.successWith(modifiedResult);
            }

            return ToolOutputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class RateLimitGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        // Track calls per user: userId -> list of call timestamps
        private static final Map<String, List<Instant>> callHistory = new ConcurrentHashMap<>();
        private static final int MAX_CALLS_PER_WINDOW = 3;
        private static final Duration TIME_WINDOW = Duration.ofMinutes(1);

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            // Extract user ID from memory ID (in real app, would come from security context)
            String userId = request.memoryId().toString();

            // Get or create call history for this user
            List<Instant> userCalls = callHistory.computeIfAbsent(userId, k -> new java.util.ArrayList<>());

            // Remove old calls outside the time window
            Instant now = Instant.now();
            Instant windowStart = now.minus(TIME_WINDOW);
            userCalls.removeIf(callTime -> callTime.isBefore(windowStart));

            // Check if rate limit exceeded
            if (userCalls.size() >= MAX_CALLS_PER_WINDOW) {
                return ToolInputGuardrailResult.failure(
                        "Rate limit exceeded. Maximum " + MAX_CALLS_PER_WINDOW + " calls per " +
                                TIME_WINDOW.toMinutes() + " minute(s). Please try again later.");
            }

            // Record this call
            userCalls.add(now);

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
            callHistory.clear();
        }
    }

    @ApplicationScoped
    public static class AuthenticationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        // Simulated security context (in real app, would use Quarkus Security)
        private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
        private static final List<String> ADMIN_USERS = List.of("admin", "superuser");

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            String user = currentUser.get();

            // Check if user is authenticated
            if (user == null) {
                return ToolInputGuardrailResult.failure(
                        "Authentication required. Please log in to use this tool.",
                        new SecurityException("Unauthenticated access attempt"));
            }

            // Check if user has required role
            if (!ADMIN_USERS.contains(user)) {
                return ToolInputGuardrailResult.failure(
                        "Insufficient permissions. Administrator role required.",
                        new SecurityException("Unauthorized access attempt by user: " + user));
            }

            return ToolInputGuardrailResult.success();
        }

        static void setCurrentUser(String user) {
            if (user == null) {
                currentUser.remove();
            } else {
                currentUser.set(user);
            }
        }

        static void reset() {
            executionCount = 0;
            currentUser.remove();
        }
    }

    @ApplicationScoped
    public static class InputSanitizationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        // Patterns for malicious input
        private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<script[^>]*>.*?</script>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            String args = request.arguments();

            // Check for script tags (XSS)
            if (SCRIPT_TAG_PATTERN.matcher(args).find()) {
                return ToolInputGuardrailResult.failure(
                        "Input validation failed: potentially malicious content detected (XSS attempt)");
            }

            // Check for HTML tags
            if (HTML_TAG_PATTERN.matcher(args).find()) {
                return ToolInputGuardrailResult.failure(
                        "Input validation failed: HTML tags not allowed");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class SqlInjectionGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        // Common SQL injection patterns
        private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
                ".*(\\bOR\\b|\\bAND\\b)\\s+\\d+\\s*=\\s*\\d+.*|.*\\bUNION\\b.*\\bSELECT\\b.*|.*\\bDROP\\b.*\\bTABLE\\b.*",
                Pattern.CASE_INSENSITIVE);

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;

            String args = request.arguments();

            // Check for SQL injection patterns
            if (SQL_INJECTION_PATTERN.matcher(args).find()) {
                return ToolInputGuardrailResult.failure(
                        "Input validation failed: SQL injection attempt detected");
            }

            return ToolInputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class OutputSizeLimitGuardrail implements ToolOutputGuardrail {
        static int executionCount = 0;

        private static final int MAX_OUTPUT_SIZE = 10000; // 10KB
        private static final int TRUNCATION_SIZE = 5000; // Keep first 5KB

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;

            String resultText = request.resultText();

            // Check if output exceeds limit
            if (resultText.length() > MAX_OUTPUT_SIZE) {
                String truncated = resultText.substring(0, TRUNCATION_SIZE) +
                        "\n\n[Output truncated - exceeded maximum size of " + MAX_OUTPUT_SIZE + " characters]";

                ToolExecutionResult modifiedResult = ToolExecutionResult.builder()
                        .resultText(truncated)
                        .build();

                return ToolOutputGuardrailResult.successWith(modifiedResult);
            }

            return ToolOutputGuardrailResult.success();
        }

        static void reset() {
            executionCount = 0;
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
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String[] segments = text.split(" - ");
                String toolName = segments[0];
                String input = segments.length > 1 ? segments[1] : "";

                return ChatResponse.builder()
                        .aiMessage(new AiMessage("executing tool", List.of(ToolExecutionRequest.builder()
                                .id("tool-id-1")
                                .name(toolName)
                                .arguments("{\"" + getParamName(toolName) + "\":\"" + input + "\"}")
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + last.text()))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("Unexpected"))
                    .build();
        }

        private String getParamName(String toolName) {
            return switch (toolName) {
                case "searchUser", "rateLimitedSearch", "secureSearch" -> "query";
                case "secureOperation" -> "data";
                case "processInput" -> "input";
                case "databaseQuery" -> "query";
                case "generateReport" -> "type";
                default -> "input";
            };
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}
