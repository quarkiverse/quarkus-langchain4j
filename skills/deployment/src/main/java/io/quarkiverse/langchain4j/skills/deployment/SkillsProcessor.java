package io.quarkiverse.langchain4j.skills.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.langchain4j.runtime.skills.SkillsConfigurator;
import io.quarkiverse.langchain4j.skills.runtime.DefaultSkillsConfigurator;
import io.quarkiverse.langchain4j.skills.runtime.SkillsRecorder;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public class SkillsProcessor {

    private static final String FEATURE = "langchain4j-skills";
    private static final String DIRECTORIES_PROPERTY = "quarkus.langchain4j.skills.directories";
    private static final String CLASSPATH_PREFIX = "classpath:";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerSkillsToolProvider(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            SkillsRecorder recorder) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(SkillsToolProvider.class)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .types(SkillsToolProvider.class)
                .supplier(recorder.toolProviderSupplier())
                .done());
    }

    @BuildStep
    void markSkillsConfiguratorUnremovable(BuildProducer<UnremovableBeanBuildItem> unremovable) {
        unremovable.produce(UnremovableBeanBuildItem.beanTypes(DefaultSkillsConfigurator.class, SkillsConfigurator.class));
    }

    /**
     * Makes sure that changes to skills trigger an application restart in dev mode.
     * Skills are loaded at runtime from the configured directories, so without this,
     * the dev mode would not consider changes to them as a reason to restart.
     */
    @BuildStep(onlyIf = IsLocalDevelopment.class)
    void watchSkillFiles(BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles) {
        List<String> directories = ConfigProvider.getConfig()
                .getOptionalValues(DIRECTORIES_PROPERTY, String.class)
                .orElse(List.of());
        for (String directory : directories) {
            if (directory.startsWith(CLASSPATH_PREFIX)) {
                String prefix = stripSlashes(directory.substring(CLASSPATH_PREFIX.length()));
                String resourcePrefix = prefix.isEmpty() ? "" : prefix + "/";
                watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                        .setLocationPredicate(location -> location.startsWith(resourcePrefix))
                        .build());
            } else {
                Path dirPath = Paths.get(directory).toAbsolutePath().normalize();
                if (!Files.isDirectory(dirPath)) {
                    continue;
                }
                // directories are watched too, so that adding or removing a skill is detected
                try (Stream<Path> paths = Files.walk(dirPath)) {
                    paths.forEach(path -> watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(path.toString())));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    private static String stripSlashes(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(start, end);
    }
}
