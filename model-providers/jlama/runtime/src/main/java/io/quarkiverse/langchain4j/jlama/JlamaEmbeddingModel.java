package io.quarkiverse.langchain4j.jlama;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.bert.BertModel;
import com.github.tjake.jlama.model.functions.Generator;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;

public class JlamaEmbeddingModel extends DimensionAwareEmbeddingModel {
    private final BertModel model;
    private final Generator.PoolingType poolingType;

    public JlamaEmbeddingModel(JlamaEmbeddingModelBuilder builder) {

        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(builder.modelCachePath);
        JlamaModel jlamaModel = RetryUtils
                .withRetry(() -> registry.downloadModel(builder.modelName, Optional.ofNullable(builder.authToken)), 3);

        if (jlamaModel.getModelType() != ModelSupport.ModelType.BERT) {
            throw new IllegalArgumentException("Model type must be BERT");
        }

        JlamaModel.Loader loader = jlamaModel.loader();
        if (builder.quantizeModelAtRuntime != null && builder.quantizeModelAtRuntime)
            loader = loader.quantized();

        if (builder.threadCount != null)
            loader = loader.threadCount(builder.threadCount);

        if (builder.workingDirectory != null)
            loader = loader.workingDirectory(builder.workingDirectory);

        loader = loader.inferenceType(AbstractModel.InferenceType.FULL_EMBEDDING);

        this.model = (BertModel) loader.load();
        this.dimension = model.getConfig().embeddingLength;

        this.poolingType = builder.poolingType == null ? Generator.PoolingType.MODEL : builder.poolingType;
    }

    public static JlamaEmbeddingModelBuilder builder() {
        return new JlamaEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();

        textSegments.forEach(textSegment -> {
            embeddings.add(Embedding.from(model.embed(textSegment.text(), poolingType)));
        });

        return Response.from(embeddings);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class JlamaEmbeddingModelBuilder {

        private Optional<Path> modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Path workingDirectory;
        private Boolean quantizeModelAtRuntime;
        private Generator.PoolingType poolingType;

        public JlamaEmbeddingModelBuilder modelCachePath(Optional<Path> modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaEmbeddingModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaEmbeddingModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaEmbeddingModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaEmbeddingModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaEmbeddingModel build() {
            return new JlamaEmbeddingModel(this);
        }
    }
}
