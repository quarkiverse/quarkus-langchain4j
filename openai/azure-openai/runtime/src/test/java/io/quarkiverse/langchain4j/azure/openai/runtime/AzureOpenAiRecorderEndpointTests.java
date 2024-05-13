package io.quarkiverse.langchain4j.azure.openai.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.azure.openai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.ImageModelConfig;
import io.quarkiverse.langchain4j.azure.openai.runtime.config.LangChain4jAzureOpenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValidationException.Problem;

class AzureOpenAiRecorderEndpointTests {
    private static final String CONFIG_ERROR_MESSAGE_TEMPLATE = "SRCFG00014: The config property quarkus.langchain4j.azure-openai.%s is required but it could not be found in any config source";

    LangChain4jAzureOpenAiConfig.AzureAiConfig config = spy(CustomAzureAiConfig.class);

    @Test
    void noEndpointConfigSet() {
        var configValidationException = catchThrowableOfType(() -> AzureOpenAiRecorder.getEndpoint(this.config,
                NamedConfigUtil.DEFAULT_NAME),
                ConfigValidationException.class);

        assertThat(configValidationException.getProblemCount())
                .isEqualTo(2);

        assertThat(configValidationException.getProblem(0))
                .isNotNull()
                .extracting(Problem::getMessage)
                .isEqualTo(String.format(CONFIG_ERROR_MESSAGE_TEMPLATE, "resource-name"));

        assertThat(configValidationException.getProblem(1))
                .isNotNull()
                .extracting(Problem::getMessage)
                .isEqualTo(String.format(CONFIG_ERROR_MESSAGE_TEMPLATE, "deployment-name"));
    }

    @Test
    void onlyResourceNameSet() {
        doReturn(Optional.of("resource-name"))
                .when(this.config)
                .resourceName();

        var configValidationException = catchThrowableOfType(() -> AzureOpenAiRecorder.getEndpoint(this.config,
                NamedConfigUtil.DEFAULT_NAME),
                ConfigValidationException.class);

        assertThat(configValidationException.getProblemCount())
                .isEqualTo(1);

        assertThat(configValidationException.getProblem(0))
                .isNotNull()
                .extracting(Problem::getMessage)
                .isEqualTo(String.format(CONFIG_ERROR_MESSAGE_TEMPLATE, "deployment-name"));
    }

    @Test
    void onlyDeploymentNameSet() {
        doReturn(Optional.of("deployment-name"))
                .when(this.config)
                .deploymentName();

        var configValidationException = catchThrowableOfType(() -> AzureOpenAiRecorder.getEndpoint(this.config,
                NamedConfigUtil.DEFAULT_NAME),
                ConfigValidationException.class);

        assertThat(configValidationException.getProblemCount())
                .isEqualTo(1);

        assertThat(configValidationException.getProblem(0))
                .isNotNull()
                .extracting(Problem::getMessage)
                .isEqualTo(String.format(CONFIG_ERROR_MESSAGE_TEMPLATE, "resource-name"));
    }

    @Test
    void endpointSet() {
        doReturn(Optional.of("https://somewhere.com"))
                .when(this.config)
                .endpoint();

        assertThat(AzureOpenAiRecorder.getEndpoint(this.config, NamedConfigUtil.DEFAULT_NAME))
                .isNotNull()
                .isEqualTo("https://somewhere.com");
    }

    @Test
    void resourceNameAndDeploymentNameSet() {
        doReturn(Optional.of("resourceName"))
                .when(this.config)
                .resourceName();

        doReturn(Optional.of("deploymentName"))
                .when(this.config)
                .deploymentName();

        assertThat(AzureOpenAiRecorder.getEndpoint(this.config, NamedConfigUtil.DEFAULT_NAME))
                .isNotNull()
                .isEqualTo(String.format(AzureOpenAiRecorder.AZURE_ENDPOINT_URL_PATTERN, "resourceName", "deploymentName"));
    }

    static class CustomLangchain4JAzureOpenAiConfig implements LangChain4jAzureOpenAiConfig {

        private final AzureAiConfig azureAiConfig;

        CustomLangchain4JAzureOpenAiConfig(AzureAiConfig azureAiConfig) {
            this.azureAiConfig = azureAiConfig;
        }

        @Override
        public AzureAiConfig defaultConfig() {
            return azureAiConfig;
        }

        @Override
        public Map<String, AzureAiConfig> namedConfig() {
            throw new IllegalStateException("should not be called");
        }
    }

    static class CustomAzureAiConfig implements LangChain4jAzureOpenAiConfig.AzureAiConfig {
        @Override
        public Optional<String> resourceName() {
            return Optional.empty();
        }

        @Override
        public Optional<String> deploymentName() {
            return Optional.empty();
        }

        @Override
        public Optional<String> endpoint() {
            return Optional.empty();
        }

        @Override
        public Optional<String> adToken() {
            return Optional.empty();
        }

        @Override
        public String apiVersion() {
            return null;
        }

        @Override
        public Optional<String> apiKey() {
            return Optional.of("my-key");
        }

        @Override
        public Duration timeout() {
            return null;
        }

        @Override
        public Integer maxRetries() {
            return null;
        }

        @Override
        public Optional<Boolean> logRequests() {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> logResponses() {
            return Optional.empty();
        }

        @Override
        public Boolean enableIntegration() {
            return null;
        }

        @Override
        public ChatModelConfig chatModel() {
            return new ChatModelConfig() {
                @Override
                public Double temperature() {
                    return null;
                }

                @Override
                public Double topP() {
                    return null;
                }

                @Override
                public Optional<Integer> maxTokens() {
                    return Optional.empty();
                }

                @Override
                public Double presencePenalty() {
                    return null;
                }

                @Override
                public Double frequencyPenalty() {
                    return null;
                }

                @Override
                public Optional<String> responseFormat() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> logRequests() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> logResponses() {
                    return Optional.empty();
                }

            };
        }

        @Override
        public EmbeddingModelConfig embeddingModel() {
            return new EmbeddingModelConfig() {
                @Override
                public Optional<Boolean> logRequests() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> logResponses() {
                    return Optional.empty();
                }
            };
        }

        @Override
        public ImageModelConfig imageModel() {
            return new ImageModelConfig() {
                @Override
                public String modelName() {
                    return null;
                }

                @Override
                public Optional<Boolean> persist() {
                    return Optional.empty();
                }

                @Override
                public Optional<Path> persistDirectory() {
                    return Optional.empty();
                }

                @Override
                public String responseFormat() {
                    return null;
                }

                @Override
                public String size() {
                    return null;
                }

                @Override
                public String quality() {
                    return null;
                }

                @Override
                public int number() {
                    return 0;
                }

                @Override
                public String style() {
                    return null;
                }

                @Override
                public Optional<String> user() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> logRequests() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> logResponses() {
                    return Optional.empty();
                }
            };
        }
    }
}
