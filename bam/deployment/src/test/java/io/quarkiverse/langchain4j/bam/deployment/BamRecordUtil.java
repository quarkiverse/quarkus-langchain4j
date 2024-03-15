package io.quarkiverse.langchain4j.bam.deployment;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessageType;
import io.quarkiverse.langchain4j.bam.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkiverse.langchain4j.bam.runtime.config.ModerationModelConfig;

public class BamRecordUtil {

    private LangChain4jBamConfig langchain4jBamConfig;

    public BamRecordUtil(LangChain4jBamConfig langchain4jBamConfig) {
        this.langchain4jBamConfig = langchain4jBamConfig;
    }

    public LangChain4jBamConfig override(List<ChatMessageType> messagesToModerate, Float implicitHate, Float hap,
            Float stigma) {
        return override(new ModerationModelConfig() {

            @Override
            public List<ChatMessageType> messagesToModerate() {

                if (messagesToModerate != null)
                    return messagesToModerate;

                return langchain4jBamConfig.defaultConfig().moderationModel().messagesToModerate();
            }

            @Override
            public Optional<Float> implicitHate() {
                return Optional.ofNullable(implicitHate);
            }

            @Override
            public Optional<Float> hap() {
                return Optional.ofNullable(hap);
            }

            @Override
            public Optional<Float> stigma() {
                return Optional.ofNullable(stigma);
            }
        });
    }

    public LangChain4jBamConfig override(Float implicitHate, Float hap, Float stigma) {
        return override(null, implicitHate, hap, stigma);
    }

    private LangChain4jBamConfig override(ModerationModelConfig config) {
        return new LangChain4jBamConfig() {

            @Override
            public BamConfig defaultConfig() {
                return new BamConfig() {

                    @Override
                    public Optional<URL> baseUrl() {
                        return langchain4jBamConfig.defaultConfig().baseUrl();
                    }

                    @Override
                    public String apiKey() {
                        return langchain4jBamConfig.defaultConfig().apiKey();
                    }

                    @Override
                    public Duration timeout() {
                        return langchain4jBamConfig.defaultConfig().timeout();
                    }

                    @Override
                    public String version() {
                        return langchain4jBamConfig.defaultConfig().version();
                    }

                    @Override
                    public Boolean logRequests() {
                        return langchain4jBamConfig.defaultConfig().logRequests();
                    }

                    @Override
                    public Boolean logResponses() {
                        return langchain4jBamConfig.defaultConfig().logResponses();
                    }

                    @Override
                    public Boolean enableIntegration() {
                        return langchain4jBamConfig.defaultConfig().enableIntegration();
                    }

                    @Override
                    public ChatModelConfig chatModel() {
                        return langchain4jBamConfig.defaultConfig().chatModel();
                    }

                    @Override
                    public EmbeddingModelConfig embeddingModel() {
                        return langchain4jBamConfig.defaultConfig().embeddingModel();
                    }

                    @Override
                    public ModerationModelConfig moderationModel() {
                        return config;
                    }

                };
            }

            @Override
            public Map<String, BamConfig> namedConfig() {
                return langchain4jBamConfig.namedConfig();
            }
        };
    }
}
