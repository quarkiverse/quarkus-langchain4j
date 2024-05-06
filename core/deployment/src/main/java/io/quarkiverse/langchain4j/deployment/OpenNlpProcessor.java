package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.JarResourceUtil.determineJarLocation;
import static io.quarkiverse.langchain4j.deployment.JarResourceUtil.matchingJarEntries;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class OpenNlpProcessor {

    @BuildStep
    void nativeResources(
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

}
