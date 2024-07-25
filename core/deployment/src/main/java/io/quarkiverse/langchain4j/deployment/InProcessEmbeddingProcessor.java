package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.runtime.InProcessEmbeddingRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

/**
 * Generate a local embedding build item for each local embedding model available in the classpath.
 * Note that the user must have the dependency for the model in their pom.xml/build.gradle.
 */
public class InProcessEmbeddingProcessor {

    // https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
    @BuildStep
    InProcessEmbeddingBuildItem all_minilm_l6_v2_q() {
        String className = "dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("all-minilm-l6-v2-q", className,
                    "all-minilm-l6-v2-q.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
    @BuildStep
    InProcessEmbeddingBuildItem all_minilm_l6_v2() {
        String className = "dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("all-minilm-l6-v2", className,
                    "all-minilm-l6-v2.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // https://huggingface.co/neuralmagic/bge-small-en-v1.5-quant
    @BuildStep
    InProcessEmbeddingBuildItem bge_small_en_q() {
        String className = "dev.langchain4j.model.embedding.onnx.bgesmallenq.BgeSmallEnQuantizedEmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("bge-small-en-q", className,
                    "bge-small-en-q.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // https://huggingface.co/BAAI/bge-small-en-v1.5
    @BuildStep
    InProcessEmbeddingBuildItem bge_small_en() {
        String className = "dev.langchain4j.model.embedding.onnx.bgesmallen.BgeSmallEnEmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("bge-small-en", className,
                    "bge-small-en.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    @BuildStep
    InProcessEmbeddingBuildItem bge_small_zh_q() {
        String className = "dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("bge-small-zh-q", className,
                    "bge-small-zh-q.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // https://huggingface.co/BAAI/bge-small-zh
    @BuildStep
    InProcessEmbeddingBuildItem bge_small_zh() {
        String className = "dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("bge-small-zh", className,
                    "bge-small-zh.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // https://huggingface.co/intfloat/e5-small-v2
    @BuildStep
    InProcessEmbeddingBuildItem e5_small_v2_q() {
        String className = "dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("e5-small-v2-q", className,
                    "e5-small-v2-q.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // https://huggingface.co/intfloat/e5-small-v2
    @BuildStep
    InProcessEmbeddingBuildItem e5_small_v2() {
        String className = "dev.langchain4j.model.embedding.onnx.e5smallv2.E5SmallV2EmbeddingModel";
        if (QuarkusClassLoader.isClassPresentAtRuntime(className)) {
            return new InProcessEmbeddingBuildItem("e5-small-v2", className,
                    "e5-small-v2.onnx", "tokenizer.json");
        } else {
            return null;
        }
    }

    // Expose a bean for each in process embedding model
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void exposeInProcessEmbeddingBeans(InProcessEmbeddingRecorder recorder,
            List<InProcessEmbeddingBuildItem> embeddings,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (InProcessEmbeddingBuildItem embedding : embeddings) {
            Optional<String> modelName = selectedEmbedding.stream()
                    .filter(se -> se.getProvider().equals(embedding.getProvider()))
                    .map(SelectedEmbeddingModelCandidateBuildItem::getConfigName)
                    .findFirst();
            var builder = SyntheticBeanBuildItem
                    .configure(DotName.createSimple(embedding.className()))
                    .types(EmbeddingModel.class)
                    .defaultBean()
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.instantiate(embedding.className()));
            modelName.ifPresent(m -> addQualifierIfNecessary(builder, m));
            beanProducer.produce(builder.done());
        }
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }
}
