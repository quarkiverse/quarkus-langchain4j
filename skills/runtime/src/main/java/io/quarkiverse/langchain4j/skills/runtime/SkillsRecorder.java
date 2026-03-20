package io.quarkiverse.langchain4j.skills.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
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
                    return new SkillsToolProvider(request -> ToolProviderResult.builder().build(), null);
                }
                List<String> directories = config.directories().get();
                List<FileSystemSkill> allSkills = new ArrayList<>();
                for (String directory : directories) {
                    // TODO: when a ClassPathSkillLoader is implemented, update this to be able to load skills from the classpath as well
                    Path dirPath = resolveDirectory(directory);
                    if (dirPath != null && Files.isDirectory(dirPath)) {
                        List<FileSystemSkill> loaded = FileSystemSkillLoader.loadSkills(dirPath);
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
                if (allSkills.isEmpty()) {
                    log.warn("No skills were loaded from any configured directory. "
                            + "The skills ToolProvider will provide no tools.");
                    return new SkillsToolProvider(request -> ToolProviderResult.builder().build(), null);
                }
                Skills skills = Skills.from(allSkills);
                return new SkillsToolProvider(skills.toolProvider(), skills);
            }
        };
    }

    private static Path resolveDirectory(String directory) {
        Path fsPath = Paths.get(directory);
        if (Files.isDirectory(fsPath)) {
            return fsPath;
        }
        return null;
    }
}
