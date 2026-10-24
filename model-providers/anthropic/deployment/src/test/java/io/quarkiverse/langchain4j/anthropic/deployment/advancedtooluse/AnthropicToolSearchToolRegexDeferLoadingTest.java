package io.quarkiverse.langchain4j.anthropic.deployment.advancedtooluse;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class AnthropicToolSearchToolRegexDeferLoadingTest extends AnthropicToolSearchToolDeferLoadingTest {
    private static final String MODEL_ID = "claude-sonnet-4-6"; // model that supports tools calling

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.model-name", MODEL_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-search.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.tool-search.type", "regex");

    @Override
    String expectedToolName() {
        return "tool_search_tool_regex";
    }

    @Override
    String expectedToolType() {
        return "tool_search_tool_regex_20251119";
    }
}
