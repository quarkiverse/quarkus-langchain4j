package io.quarkiverse.langchain4j.skills.runtime;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SkillsRecorder {

    private static final Logger log = Logger.getLogger(SkillsRecorder.class);

    private final RuntimeValue<SkillsConfiguration> configuration;

    public SkillsRecorder(RuntimeValue<SkillsConfiguration> configuration) {
        this.configuration = configuration;
    }

    public Supplier<SkillsToolProvider> toolProviderSupplier() {
        return new Supplier<>() {
            @Override
            public SkillsToolProvider get() {
                SkillsConfiguration config = configuration.getValue();
                if (config.directories().isEmpty() || config.directories().get().isEmpty()) {
                    log.warn("No skills directories configured (quarkus.langchain4j.skills.directories). "
                            + "The skills ToolProvider will provide no tools.");
                    return new SkillsToolProvider(request -> ToolProviderResult.builder().build(), null, List.of());
                }
                List<String> directories = config.directories().get();
                List<Skill> allSkills = new ArrayList<>();
                for (String directory : directories) {
                    if (directory.startsWith("classpath:")) {
                        String dir = directory.substring("classpath:".length());
                        ensureResourceFileSystemExists(dir);
                        List<Skill> loaded = ClassPathSkillLoader.loadSkills(dir);
                        if (!loaded.isEmpty()) {
                            log.infof("Loaded %d skill(s) from directory: %s", loaded.size(), directory);
                            allSkills.addAll(loaded);
                        } else {
                            log.warnf("No skills found in directory: %s", directory);
                        }
                    } else {
                        Path dirPath = resolveDirectory(directory);
                        if (dirPath != null && Files.isDirectory(dirPath)) {
                            List<? extends Skill> loaded = FileSystemSkillLoader.loadSkills(dirPath);
                            if (!loaded.isEmpty()) {
                                log.infof("Loaded %d skill(s) from directory: %s", loaded.size(), dirPath.toAbsolutePath());
                                allSkills.addAll(loaded);
                            } else {
                                log.warnf("No skills found in directory: %s", dirPath.toAbsolutePath());
                            }
                        } else {
                            log.warnf("Skills directory does not exist or is not a directory: %s", directory);
                        }
                    }
                }
                if (allSkills.isEmpty()) {
                    log.warn("No skills were loaded from any configured directory. "
                            + "The skills ToolProvider will provide no tools.");
                    return new SkillsToolProvider(request -> ToolProviderResult.builder().build(), null, List.of());
                }
                Skills skills = Skills.from(allSkills);
                return new SkillsToolProvider(skills.toolProvider(), skills, allSkills);
            }
        };
    }

    /**
     * In a native image, classpath resources are exposed through {@code resource:} URIs whose file system
     * has to be created explicitly before {@link Path#of(URI)} can be used on them
     * (which is what {@link ClassPathSkillLoader} does).
     */
    private static void ensureResourceFileSystemExists(String directoryOnClasspath) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(directoryOnClasspath);
        if (url == null || !"resource".equals(url.getProtocol())) {
            return;
        }
        try {
            FileSystems.newFileSystem(URI.create("resource:/"), Map.of());
        } catch (FileSystemAlreadyExistsException e) {
            // already created, nothing to do
        } catch (IOException e) {
            throw new RuntimeException("Failed to create the native image resource file system", e);
        }
    }

    private static Path resolveDirectory(String directory) {
        Path fsPath = Paths.get(directory);
        if (Files.isDirectory(fsPath)) {
            return fsPath;
        }
        return null;
    }
}
