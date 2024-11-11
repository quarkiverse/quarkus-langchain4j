package io.quarkiverse.langchain4j.openai.common;

import static io.quarkiverse.langchain4j.deployment.JarResourceUtil.determineJarLocation;
import static io.quarkiverse.langchain4j.deployment.JarResourceUtil.matchingJarEntries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import com.knuddels.jtokkit.Encodings;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyReaderOverrideBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyWriterOverrideBuildItem;
import io.smallrye.config.Priorities;

public class OpenAiCommonProcessor {

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.ai4j", "openai4j"));
    }

    @BuildStep
    void nativeImageSupport(BuildProducer<NativeImageResourceBuildItem> resourcesProducer) {
        registerJtokkitResources(resourcesProducer);
    }

    private void registerJtokkitResources(BuildProducer<NativeImageResourceBuildItem> resourcesProducer) {
        List<String> resources = new ArrayList<>();
        try (JarFile jarFile = new JarFile(determineJarLocation(Encodings.class).toFile())) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                if (name.endsWith(".tiktoken")) {
                    resources.add(name);
                }

            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> names = matchingJarEntries(determineJarLocation(Encodings.class),
                e -> e.getName().endsWith(".tiktoken")).stream().map(ZipEntry::getName).collect(Collectors.toList());
        if (!names.isEmpty()) {
            resourcesProducer.produce(new NativeImageResourceBuildItem(resources));
        }
    }

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that Jackson is used.
     * This is not a proper solution as it affects all clients, but it's better than the having the reader/writers be selected
     * at random.
     */
    @BuildStep
    public void deprioritizeJsonb(Capabilities capabilities,
            BuildProducer<MessageBodyReaderOverrideBuildItem> readerOverrideProducer,
            BuildProducer<MessageBodyWriterOverrideBuildItem> writerOverrideProducer) {
        if (capabilities.isPresent(Capability.REST_CLIENT_REACTIVE_JSONB)) {
            readerOverrideProducer.produce(
                    new MessageBodyReaderOverrideBuildItem("org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyReader",
                            Priorities.APPLICATION + 1, true));
            writerOverrideProducer.produce(new MessageBodyWriterOverrideBuildItem(
                    "org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyWriter", Priorities.APPLICATION + 1, true));
        }
    }
}
