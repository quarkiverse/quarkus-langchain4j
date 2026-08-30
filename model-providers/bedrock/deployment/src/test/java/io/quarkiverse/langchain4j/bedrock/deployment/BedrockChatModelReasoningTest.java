package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockChatModelReasoningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id",
                    "us.anthropic.claude-opus-4-8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.reasoning-effort", "medium")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.legacy.chat-model.reasoning", "1024")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.legacy.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.legacy.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.both.chat-model.reasoning", "1024")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.both.chat-model.reasoning-effort", "high")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.both.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.both.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider");

    @Inject
    ChatModel adaptiveModel;

    @Inject
    @ModelName("legacy")
    ChatModel legacyModel;

    @Inject
    @ModelName("both")
    ChatModel bothModel;

    @Test
    void reasoning_effort_should_enable_adaptive_reasoning() {
        var fields = additionalModelRequestFields(adaptiveModel);

        assertThat(fields).containsEntry("reasoning_config", Map.of("type", "adaptive"));
        assertThat(fields).containsEntry("output_config", Map.of("effort", "medium"));
    }

    @Test
    void reasoning_budget_should_enable_legacy_reasoning() {
        var fields = additionalModelRequestFields(legacyModel);

        assertThat(fields).containsEntry("reasoning_config", Map.of("type", "enabled", "budget_tokens", 1024));
        assertThat(fields).doesNotContainKey("output_config");
    }

    @Test
    void reasoning_effort_should_take_precedence_over_legacy_budget() {
        var fields = additionalModelRequestFields(bothModel);

        assertThat(fields).containsEntry("reasoning_config", Map.of("type", "adaptive"));
        assertThat(fields).containsEntry("output_config", Map.of("effort", "high"));
    }

    private static Map<String, Object> additionalModelRequestFields(ChatModel model) {
        var bedrockModel = (BedrockChatModel) ClientProxy.unwrap(model);
        var parameters = (BedrockChatRequestParameters) bedrockModel.defaultRequestParameters();
        return parameters.additionalModelRequestFields();
    }
}
