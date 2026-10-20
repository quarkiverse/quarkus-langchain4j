package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A named model with its own endpoint override and region still reaches the mock: the mock overrides the endpoint at
 * the model level too, and a configured region is kept.
 */
public class BedrockNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.bedrock.b1.chat-model.model-id=anthropic.claude-3-haiku
                            quarkus.langchain4j.bedrock.b1.chat-model.aws.endpoint-override=http://localhost:1
                            quarkus.langchain4j.bedrock.b1.chat-model.aws.region=eu-west-1
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.b1.chat-model.provider", "bedrock");

    @Inject
    @ModelName("b1")
    ChatModel b1;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.chat().reply("from b1");

        assertThat(b1.chat("hi")).isEqualTo("from b1");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/bedrock/model/anthropic.claude-3-haiku/converse");
        assertThat(r.asChat().modelName()).isEqualTo("anthropic.claude-3-haiku");
        assertThat(r.header("Authorization")).contains("Credential=mock-access-key/").contains("/eu-west-1/bedrock/");
    }
}
