package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.bedrock.BedrockCachePointPlacement;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;
import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockChatModelPromptCachingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.prompt-caching", "AFTER_SYSTEM")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.prompt-caching-ttl", "VALUE_1_H")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.default-ttl.chat-model.prompt-caching", "AFTER_USER_MESSAGE")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.default-ttl.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.default-ttl.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.ttl-only.chat-model.prompt-caching-ttl", "VALUE_1_H")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.ttl-only.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.ttl-only.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.aws-value.chat-model.prompt-caching", "AFTER_TOOLS")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.aws-value.chat-model.prompt-caching-ttl", "1h")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.aws-value.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.aws-value.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider");

    @Inject
    ChatModel oneHourModel;

    @Inject
    @ModelName("default-ttl")
    ChatModel defaultTtlModel;

    @Inject
    @ModelName("ttl-only")
    ChatModel ttlOnlyModel;

    @Inject
    @ModelName("aws-value")
    ChatModel awsValueModel;

    @Test
    void prompt_caching_ttl_should_be_applied() {
        var parameters = parameters(oneHourModel);

        assertThat(parameters.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_SYSTEM);
        assertThat(parameters.cacheTtl()).isEqualTo(CacheTTL.VALUE_1_H);
    }

    @Test
    void prompt_caching_without_ttl_should_keep_the_provider_default() {
        var parameters = parameters(defaultTtlModel);

        assertThat(parameters.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_USER_MESSAGE);
        assertThat(parameters.cacheTtl()).isNull();
    }

    @Test
    void ttl_without_prompt_caching_should_be_ignored() {
        var parameters = parameters(ttlOnlyModel);

        assertThat(parameters.cachePointPlacement()).isNull();
        assertThat(parameters.cacheTtl()).isNull();
    }

    @Test
    void aws_ttl_value_should_be_accepted() {
        var parameters = parameters(awsValueModel);

        assertThat(parameters.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_TOOLS);
        assertThat(parameters.cacheTtl()).isEqualTo(CacheTTL.VALUE_1_H);
    }

    private static BedrockChatRequestParameters parameters(ChatModel model) {
        var bedrockModel = (BedrockChatModel) ClientProxy.unwrap(model);
        return (BedrockChatRequestParameters) bedrockModel.defaultRequestParameters();
    }
}
