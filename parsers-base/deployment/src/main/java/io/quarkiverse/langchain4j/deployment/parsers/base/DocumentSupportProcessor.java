package io.quarkiverse.langchain4j.deployment.parsers.base;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jboss.logmanager.Level;

import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LogCategoryBuildItem;
import io.quarkus.deployment.builditem.nativeimage.*;

/**
 * TODO: we might want to make this more granular so all these document related dependencies don't always end up in the
 * application
 */
public class DocumentSupportProcessor {

    @BuildStep
    void nativeResources(
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingBuildItems,
            BuildProducer<NativeImageResourcePatternsBuildItem> nativePatternProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectionProducer) {
        if (!inProcessEmbeddingBuildItems.isEmpty()) {
            // TODO: we can do better here and only include the target architecture's libs
            nativePatternProducer
                    .produce(NativeImageResourcePatternsBuildItem.builder()
                            .includeGlobs("ai/onnxruntime/native/**", "native/lib/**").build());

            reflectionProducer
                    .produce(ReflectiveClassBuildItem.builder("opennlp.tools.sentdetect.SentenceDetectorFactory").build());
            reflectionProducer.produce(ReflectiveClassBuildItem.builder("ai.onnxruntime.OnnxTensor").methods(true).build());
        }
    }

    @BuildStep
    void runtimeClasses(
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
                "ai.onnxruntime.OrtSession",
                "org.apache.fontbox.ttf.RAFDataStream",
                "org.apache.fontbox.ttf.TTFParser",
                "org.apache.pdfbox.pdmodel.encrypetion.PublicKeySecurityHandler",
                "org.apache.pdfbox.pdmodel.font.FileSystemFontProvider$FSFontInfo",
                "org.apache.pdfbox.pdmodel.font.FontMapperImpl$DefaultFontProvider",
                "org.apache.pdfbox.pdmodel.font.FontMapperImpl",
                "org.apache.pdfbox.pdmodel.font.FontMappers$DefaultFontMapper",
                "org.apache.pdfbox.pdmodel.font.PDFont",
                "org.apache.pdfbox.pdmodel.font.PDFontLike",
                "org.apache.pdfbox.pdmodel.font.PDSimpleFont",
                "org.apache.pdfbox.pdmodel.font.PDType1Font",
                "org.apache.pdfbox.pdmodel.graphics.color.PDCIEDictionaryBasedColorSpace",
                "org.apache.pdfbox.pdmodel.PDDocument",
                "org.apache.pdfbox.rendering.SoftMask")
                .filter(QuarkusClassLoader::isClassPresentAtRuntime)
                .map(RuntimeInitializedClassBuildItem::new).forEach(classProducer::produce);

        for (InProcessEmbeddingBuildItem inProcessEmbeddingBuildItem : inProcessEmbeddingBuildItems) {
            classProducer.produce(new RuntimeInitializedClassBuildItem(inProcessEmbeddingBuildItem.className()));
        }
        classProducer.produce(new RuntimeInitializedClassBuildItem(CompressingQueryTransformer.class.getName()));
        classProducer.produce(new RuntimeInitializedClassBuildItem(CompressorStreamFactory.class.getName()));
        classProducer.produce(new RuntimeInitializedClassBuildItem(
                "org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream"));

        packageProducer.produce(new RuntimeInitializedPackageBuildItem("com.microsoft.schemas.office"));
    }

    @BuildStep
    void includeInProcessEmbeddingModels(
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

    @BuildStep
    void quietDownLogging(BuildProducer<LogCategoryBuildItem> producer) {
        producer.produce(new LogCategoryBuildItem("ai.djl.util.Platform", Level.WARN));
    }
}
