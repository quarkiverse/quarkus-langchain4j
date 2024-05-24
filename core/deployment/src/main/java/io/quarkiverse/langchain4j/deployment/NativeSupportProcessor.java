package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.JarResourceUtil.determineJarLocation;
import static io.quarkiverse.langchain4j.deployment.JarResourceUtil.matchingJarEntries;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

public class NativeSupportProcessor {

    // this is needed because under some circumstances GraalVM seems to try and load it at build tiume
    @BuildStep
    void jackson(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClassProducer) {
        runtimeInitializedClassProducer.produce(new RuntimeInitializedClassBuildItem(
                QuarkusJsonCodecFactory.ObjectMapperHolder.class.getName()));
    }

    @BuildStep
    void openNlp(
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectionProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) {
        if (!inProcessEmbeddingBuildItems.isEmpty()) {
            reflectionProducer
                    .produce(ReflectiveClassBuildItem.builder("opennlp.tools.sentdetect.SentenceDetectorFactory").build());
        }
        Path langChain4jJar = determineJarLocation(DocumentBySentenceSplitter.class);
        List<String> names = matchingJarEntries(langChain4jJar, e -> e.getName().endsWith(".bin")).stream().map(
                ZipEntry::getName).collect(Collectors.toList());
        if (!names.isEmpty()) {
            nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(names));
        }
    }

    // this will come into play when the upstream LangChain4j in-memory-embeddings dependencies are used
    @BuildStep
    void aiDlj(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClassProducer) {
        runtimeInitializedClassProducer.produce(new RuntimeInitializedClassBuildItem("ai.djl.engine.Engine"));
    }

}
