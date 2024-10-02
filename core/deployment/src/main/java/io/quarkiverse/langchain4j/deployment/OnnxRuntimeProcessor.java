package io.quarkiverse.langchain4j.deployment;

import java.util.List;
import java.util.stream.Stream;

import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;

/**
 * A processor configuring the native image build for the OnnxRuntime.
 * Only enabled if the `RequireOnnxRuntimeBuildItem` build item is present.
 */
public class OnnxRuntimeProcessor {

    @BuildStep
    @Consume(RequireOnnxRuntimeBuildItem.class)
    void onxxRuntimeNative(
            BuildProducer<NativeImageResourcePatternsBuildItem> nativePatternProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectionProducer,
            BuildProducer<JniRuntimeAccessBuildItem> jniProducer) {
        List<String> classesInstantiatedFromNative = List.of(
                "ai.onnxruntime.TensorInfo",
                "ai.onnxruntime.SequenceInfo",
                "ai.onnxruntime.MapInfo",
                "ai.onnxruntime.OrtException",
                "ai.onnxruntime.OnnxSparseTensor");

        reflectionProducer.produce(
                ReflectiveClassBuildItem.builder(classesInstantiatedFromNative.toArray(new String[0]))
                        .fields().methods().constructors().build());

        jniProducer.produce(
                new JniRuntimeAccessBuildItem(true, true, true, classesInstantiatedFromNative.toArray(new String[0])));

        // TODO should only select the target architecture's libs
        nativePatternProducer
                .produce(NativeImageResourcePatternsBuildItem.builder()
                        .includeGlobs("ai/onnxruntime/native/**", "native/lib/**").build());

        reflectionProducer
                .produce(ReflectiveClassBuildItem.builder("opennlp.tools.sentdetect.SentenceDetectorFactory").build());
        reflectionProducer.produce(
                ReflectiveClassBuildItem.builder("ai.onnxruntime.OnnxTensor").methods().fields().constructors().build());
    }

    @BuildStep
    @Consume(RequireOnnxRuntimeBuildItem.class)
    void onnxRuntimeClasses(
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingBuildItems,
            BuildProducer<RuntimeInitializedClassBuildItem> classProducer,
            BuildProducer<RuntimeInitializedPackageBuildItem> packageProducer) {
        Stream.of(
                "dev.langchain4j.model.embedding.OnnxBertBiEncoder",
                "dev.langchain4j.model.embedding.HuggingFaceTokenizer",
                "ai.djl.huggingface.tokenizers.HuggingFaceTokenizer",
                "ai.djl.huggingface.tokenizers.jni.TokenizersLibrary",
                "ai.djl.huggingface.tokenizers.jni.LibUtils",
                "ai.djl.util.Platform",
                "ai.onnxruntime.OrtEnvironment",
                "ai.onnxruntime.OnnxRuntime",
                "ai.onnxruntime.OnnxTensorLike",
                "ai.onnxruntime.OrtAllocator",
                "ai.onnxruntime.OrtSession$SessionOptions",
                "ai.onnxruntime.OrtSession")
                .filter(QuarkusClassLoader::isClassPresentAtRuntime)
                .map(RuntimeInitializedClassBuildItem::new).forEach(classProducer::produce);
    }

}
