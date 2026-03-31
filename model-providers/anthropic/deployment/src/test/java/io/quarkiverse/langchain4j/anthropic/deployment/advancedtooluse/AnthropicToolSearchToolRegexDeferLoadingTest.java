package io.quarkiverse.langchain4j.anthropic.deployment.advancedtooluse;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class AnthropicToolSearchToolRegexDeferLoadingTest extends AnthropicToolSearchToolDeferLoadingTest {
    private static final String MODEL_ID = "claude-sonnet-4-6"; // model that supports tools calling

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-search.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-search.type", "regex")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.base-url", "http://localhost:%d".formatted(WIREMOCK_PORT));

    @Override
    String expectedToolName() {
        return "tool_search_tool_regex";
    }

    @Override
    String expectedToolType() {
        return "tool_search_tool_regex_20251119";
    }
}
