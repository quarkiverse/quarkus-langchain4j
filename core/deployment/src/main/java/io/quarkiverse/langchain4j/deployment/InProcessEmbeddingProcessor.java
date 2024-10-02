package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

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
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * Generate a local embedding build item for each local embedding model available in the classpath.
 * Note that the user must have the dependency for the model in their pom.xml/build.gradle.
 */
public class InProcessEmbeddingProcessor {

    record LocalEmbeddingModel(String classname, String modelName, String onnxModelPath, String vocabularyPath) {
    }

    private static final Logger LOGGER = Logger.getLogger(InProcessEmbeddingProcessor.class);

    private static List<LocalEmbeddingModel> MODELS = List.of(
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel",
                    "all-minilm-l6-v2-q", "all-minilm-l6-v2-q.onnx", "all-minilm-l6-v2-q-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel",
                    "all-minilm-l6-v2",
                    "all-minilm-l6-v2.onnx", "all-minilm-l6-v2-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.bgesmallenq.BgeSmallEnQuantizedEmbeddingModel",
                    "bge-small-en-q",
                    "bge-small-en-q.onnx", "bge-small-en-q-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.bgesmallen.BgeSmallEnEmbeddingModel", "bge-small-en",
                    "bge-small-en.onnx", "bge-small-en-tokenizer.json"),
            // Add BGE 1.5 - on hold for now - see https://github.com/quarkiverse/quarkus-langchain4j/issues/897#issuecomment-2387691937
            //            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel",
            //                    "bge-small-en-v1.5-q", "bge-small-en-v1.5-q.onnx", "bge-small-en-v1.5-q-tokenizer.json"),
            //            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel",
            //                    "bge-small-en-v1.5",
            //                    "bge-small-en-v1.5.onnx", "bge-small-en-v1.5-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel",
                    "bge-small-zh-q",
                    "bge-small-zh-q.onnx", "bge-small-zh-q-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel", "bge-small-zh",
                    "bge-small-zh.onnx", "bge-small-zh-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel",
                    "e5-small-v2-q",
                    "e5-small-v2-q.onnx", "e5-small-v2-q-tokenizer.json"),
            new LocalEmbeddingModel("dev.langchain4j.model.embedding.onnx.e5smallv2.E5SmallV2EmbeddingModel", "e5-small-v2",
                    "e5-small-v2.onnx", "e5-small-v2-tokenizer.json"));

    @BuildStep
    public void generateLocalEmbeddingBuildItems(BuildProducer<InProcessEmbeddingBuildItem> producer) {
        for (LocalEmbeddingModel model : MODELS) {
            if (QuarkusClassLoader.isClassPresentAtRuntime(model.classname())) {
                LOGGER.debugf("%s found in the runtime classpath", model.classname());
                if (!QuarkusClassLoader.isResourcePresentAtRuntime(model.onnxModelPath())) {
                    throw new RuntimeException(
                            "Model " + model.modelName() + " is missing the ONNX model file: " + model.onnxModelPath());
                }
                if (!QuarkusClassLoader.isResourcePresentAtRuntime(model.vocabularyPath())) {
                    throw new RuntimeException(
                            "Model " + model.modelName() + " is missing the vocabulary file: " + model.vocabularyPath());
                }
                producer.produce(new InProcessEmbeddingBuildItem(model.modelName(), model.classname(), model.onnxModelPath(),
                        model.vocabularyPath()));
            }
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

    @BuildStep
    void includeInProcessEmbeddingModelsInNativeExecutable(
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingBuildItems,
            BuildProducer<NativeImageResourceBuildItem> resources,
            BuildProducer<ReflectiveClassBuildItem> reflection) {
        for (InProcessEmbeddingBuildItem inProcessEmbeddingBuildItem : inProcessEmbeddingBuildItems) {
            resources.produce(new NativeImageResourceBuildItem(inProcessEmbeddingBuildItem.onnxModelPath()));
            resources.produce(new NativeImageResourceBuildItem(inProcessEmbeddingBuildItem.vocabularyPath()));
            reflection.produce(ReflectiveClassBuildItem.builder(inProcessEmbeddingBuildItem.className())
                    .constructors(true)
                    .fields(true)
                    .methods(true)
                    .build());
        }
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }
}
