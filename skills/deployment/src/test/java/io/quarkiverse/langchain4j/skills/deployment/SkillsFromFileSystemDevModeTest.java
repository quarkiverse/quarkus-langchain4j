package io.quarkiverse.langchain4j.skills.deployment;

import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * Verifies that changes to skills loaded from a filesystem directory are picked up in dev mode.
 */
public class SkillsFromFileSystemDevModeTest {

    static final Path SKILLS_DIR = createSkillsDir();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SkillsDevModeTest.SkillResource.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.skills.directories=" + SKILLS_DIR),
                            "application.properties"));

    @Test
    void skillChangeIsPickedUp() throws IOException {
        RestAssured.get("/skill/fs-skill").then()
                .statusCode(200)
                .body(containsString("Original fs skill content."));

        Path skillFile = SKILLS_DIR.resolve("fs-skill/SKILL.md");
        Files.writeString(skillFile, Files.readString(skillFile)
                .replace("Original fs skill content.", "Modified fs skill content."));
        // make sure the change is detected even on filesystems with coarse timestamp granularity
        Files.setLastModifiedTime(skillFile,
                FileTime.fromMillis(System.currentTimeMillis() + 2000));

        RestAssured.get("/skill/fs-skill").then()
                .statusCode(200)
                .body(containsString("Modified fs skill content."));
    }

    private static Path createSkillsDir() {
        try {
            Path dir = Files.createTempDirectory("skills-devmode");
            Path skillDir = Files.createDirectories(dir.resolve("fs-skill"));
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: fs-skill
                    description: A skill loaded from the filesystem
                    ---

                    Original fs skill content.
                    """);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
