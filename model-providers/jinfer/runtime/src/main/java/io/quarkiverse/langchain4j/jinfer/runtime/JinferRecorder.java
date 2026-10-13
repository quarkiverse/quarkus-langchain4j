package io.quarkiverse.langchain4j.jinfer.runtime;

import java.util.List;
import java.util.function.Supplier;

import com.qxotic.jinfer.langchain4j.JinferChatModel;
import com.qxotic.jinfer.langchain4j.JinferEmbeddingModel;
import com.qxotic.jinfer.langchain4j.JinferScoringModel;
import com.qxotic.jinfer.langchain4j.JinferSpeechModel;
import com.qxotic.jinfer.langchain4j.JinferTranscriptionModel;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.jinfer.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.EmbeddingModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.LangChain4jJinferConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.ScoringModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.TranscriptionModelConfig;
import io.quarkiverse.langchain4j.jinfer.runtime.config.TtsModelConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JinferRecorder {

    private final RuntimeValue<LangChain4jJinferConfig> runtimeConfig;

    public JinferRecorder(RuntimeValue<LangChain4jJinferConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Closes every loaded model once, at application shutdown. jinfer models are {@link AutoCloseable}
     * and hold large in-memory weights, so they must be released explicitly. The registry is static
     * (see {@link JinferModels}) because this build step's recorder instance differs from the ones
     * that created the models.
     */
    public void closeModelsAtShutdown(ShutdownContext shutdown) {
        shutdown.addLastShutdownTask(new Runnable() {
            @Override
            public void run() {
                JinferModels.closeAll();
            }
        });
    }

    public Supplier<ChatModel> chatModel(final String configName) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return new Supplier<ChatModel>() {
                @Override
                public ChatModel get() {
                    return new DisabledChatModel();
                }
            };
        }
        return new Supplier<ChatModel>() {
            @Override
            public ChatModel get() {
                return getOrCreateChatModel(configName);
            }
        };
    }

    public Supplier<StreamingChatModel> streamingChatModel(final String configName) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return new Supplier<StreamingChatModel>() {
                @Override
                public StreamingChatModel get() {
                    return new DisabledStreamingChatModel();
                }
            };
        }
        return new Supplier<StreamingChatModel>() {
            @Override
            public StreamingChatModel get() {
                return getOrCreateChatModel(configName).streaming();
            }
        };
    }

    public Supplier<EmbeddingModel> embeddingModel(final String configName) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return new Supplier<EmbeddingModel>() {
                @Override
                public EmbeddingModel get() {
                    return new DisabledEmbeddingModel();
                }
            };
        }
        return new Supplier<EmbeddingModel>() {
            @Override
            public EmbeddingModel get() {
                EmbeddingModelConfig emb = correspondingConfig(configName).embeddingModel();
                JinferEmbeddingModel.Builder builder = JinferEmbeddingModel.builder();
                if (emb.modelPath().isPresent()) {
                    builder.modelPath(emb.modelPath().get());
                } else if (emb.model().isPresent()) {
                    builder.model(emb.model().get());
                } else {
                    throw missingModel(configName, "embedding-model");
                }
                if (emb.contextCapacity().isPresent()) {
                    builder.contextCapacity(emb.contextCapacity().getAsInt());
                }
                JinferEmbeddingModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    public Supplier<ScoringModel> scoringModel(final String configName) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return new Supplier<ScoringModel>() {
                @Override
                public ScoringModel get() {
                    return new DisabledScoringModel();
                }
            };
        }
        return new Supplier<ScoringModel>() {
            @Override
            public ScoringModel get() {
                ScoringModelConfig scoring = correspondingConfig(configName).scoringModel();
                JinferScoringModel.Builder builder = JinferScoringModel.builder();
                if (scoring.modelPath().isPresent()) {
                    builder.modelPath(scoring.modelPath().get());
                } else if (scoring.model().isPresent()) {
                    builder.model(scoring.model().get());
                } else {
                    throw missingModel(configName, "scoring-model");
                }
                if (scoring.contextCapacity().isPresent()) {
                    builder.contextCapacity(scoring.contextCapacity().getAsInt());
                }
                if (scoring.instruction().isPresent()) {
                    builder.instruction(scoring.instruction().get());
                }
                JinferScoringModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    public Supplier<AudioTranscriptionModel> transcriptionModel(final String configName) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return new Supplier<AudioTranscriptionModel>() {
                @Override
                public AudioTranscriptionModel get() {
                    return new DisabledTranscriptionModel();
                }
            };
        }
        return new Supplier<AudioTranscriptionModel>() {
            @Override
            public AudioTranscriptionModel get() {
                TranscriptionModelConfig transcription = correspondingConfig(configName).transcriptionModel();
                JinferTranscriptionModel.Builder builder = JinferTranscriptionModel.builder();
                if (transcription.modelPath().isPresent()) {
                    builder.modelPath(transcription.modelPath().get());
                } else if (transcription.model().isPresent()) {
                    builder.model(transcription.model().get());
                } else {
                    throw missingModel(configName, "transcription-model");
                }
                JinferTranscriptionModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    public Supplier<TextToSpeechModel> ttsModel(final String configName) {
        if (!correspondingConfig(configName).enableIntegration()) {
            return new Supplier<TextToSpeechModel>() {
                @Override
                public TextToSpeechModel get() {
                    return new DisabledTtsModel();
                }
            };
        }
        return new Supplier<TextToSpeechModel>() {
            @Override
            public TextToSpeechModel get() {
                TtsModelConfig tts = correspondingConfig(configName).ttsModel();
                JinferSpeechModel.Builder builder = JinferSpeechModel.builder();
                if (tts.modelPath().isPresent()) {
                    builder.modelPath(tts.modelPath().get());
                } else if (tts.model().isPresent()) {
                    builder.model(tts.model().get());
                } else {
                    throw missingModel(configName, "tts-model");
                }
                if (tts.speed().isPresent()) {
                    builder.speed(tts.speed().getAsDouble());
                }
                if (tts.maxInputChars().isPresent()) {
                    builder.maxInputChars(tts.maxInputChars().getAsInt());
                }
                JinferSpeechModel model = builder.build();
                JinferModels.registerForClose(model);
                return model;
            }
        };
    }

    private JinferChatModel getOrCreateChatModel(String configName) {
        JinferChatModel existing = JinferModels.getChatModel(configName);
        if (existing != null) {
            return existing;
        }
        return JinferModels.storeChatModelIfAbsent(configName, buildChatModel(configName));
    }

    private JinferChatModel buildChatModel(String configName) {
        ChatModelConfig chat = correspondingConfig(configName).chatModel();
        JinferChatModel.Builder builder = JinferChatModel.builder();
        if (chat.modelPath().isPresent()) {
            builder.modelPath(chat.modelPath().get());
        } else if (chat.model().isPresent()) {
            builder.model(chat.model().get());
        } else {
            throw missingModel(configName, "chat-model");
        }
        if (chat.contextCapacity().isPresent()) {
            builder.contextCapacity(chat.contextCapacity().getAsInt());
        }
        builder.temperature(chat.temperature());
        if (chat.topP().isPresent()) {
            builder.topP(chat.topP().getAsDouble());
        }
        if (chat.topK().isPresent()) {
            builder.topK(chat.topK().getAsInt());
        }
        if (chat.minP().isPresent()) {
            builder.minP(chat.minP().getAsDouble());
        }
        if (chat.maxOutputTokens().isPresent()) {
            builder.maxOutputTokens(chat.maxOutputTokens().getAsInt());
        }
        if (chat.maxReasoningTokens().isPresent()) {
            builder.maxReasoningTokens(chat.maxReasoningTokens().getAsInt());
        }
        if (chat.seed().isPresent()) {
            builder.seed(chat.seed().getAsLong());
        }
        if (chat.promptCache().isPresent()) {
            builder.promptCache(chat.promptCache().get());
        }
        if (chat.thinking().isPresent()) {
            builder.thinking(chat.thinking().get());
        }
        return builder.build();
    }

    private LangChain4jJinferConfig.JinferConfig correspondingConfig(String configName) {
        return NamedConfigUtil.isDefault(configName)
                ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private static IllegalStateException missingModel(String configName, String typeKey) {
        String key = "quarkus.langchain4j.jinfer." + qualify(configName, typeKey);
        return new IllegalStateException(
                "Jinfer " + typeKey + " requires either '" + key + ".model' or '" + key + ".model-path' to be set");
    }

    private static String qualify(String configName, String typeKey) {
        return NamedConfigUtil.isDefault(configName) ? typeKey : "\"" + configName + "\"." + typeKey;
    }

    private static final class DisabledScoringModel implements ScoringModel {
        @Override
        public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
            throw new ModelDisabledException("ScoringModel is disabled");
        }
    }

    private static final class DisabledTranscriptionModel implements AudioTranscriptionModel {
        @Override
        public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest request) {
            throw new ModelDisabledException("AudioTranscriptionModel is disabled");
        }
    }

    private static final class DisabledTtsModel implements TextToSpeechModel {
        @Override
        public TextToSpeechResponse synthesize(TextToSpeechRequest request) {
            throw new ModelDisabledException("TextToSpeechModel is disabled");
        }
    }
}
