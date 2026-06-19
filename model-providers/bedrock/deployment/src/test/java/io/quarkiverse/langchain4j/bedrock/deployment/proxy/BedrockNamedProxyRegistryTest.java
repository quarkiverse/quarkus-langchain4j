package io.quarkiverse.langchain4j.bedrock.deployment.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.bedrock.deployment.BedrockTestBase;
import io.quarkiverse.langchain4j.bedrock.deployment.TestCredentialsProvider;
import io.quarkus.test.QuarkusUnitTest;

class BedrockNamedProxyRegistryTest extends BedrockTestBase {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            // Nothing listens on this port: the request can only succeed when routed through the proxy.
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.endpoint-override",
                    "http://localhost:18299")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.client.proxy-configuration-name", "local")
            .overrideRuntimeConfigKey("quarkus.proxy.local.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.local.port", String.valueOf(WM_PORT));

    @Inject
    ChatModel chatModel;

    @Test
    void should_route_request_through_named_proxy() {
        stubFor(post(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "output": {
                                    "message": {
                                      "role": "assistant",
                                      "content": [
                                        {
                                          "text": "Routed through named proxy"
                                        }
                                      ]
                                    }
                                  },
                                  "stopReason": "end_turn",
                                  "usage": {
                                    "inputTokens": 5,
                                    "outputTokens": 4,
                                    "totalTokens": 9
                                  },
                                  "metrics": {
                                    "latencyMs": 1
                                  }
                                }
                                """)));

        var response = chatModel.chat("Hello");

        assertThat(response).isEqualTo("Routed through named proxy");
        verify(postRequestedFor(anyUrl()));
    }
}
