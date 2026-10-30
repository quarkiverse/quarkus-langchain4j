package io.quarkiverse.langchain4j.skills.deployment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * Verifies that changes to skills loaded from the classpath are picked up in dev mode.
 */
public class SkillsDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SkillResource.class)
                    .addAsResource("skills/foobar-skill/SKILL.md", "skills/foobar-skill/SKILL.md")
                    .addAsResource(new StringAsset("quarkus.langchain4j.skills.directories=classpath:skills"),
                            "application.properties"));

    @Test
    void skillChangeIsPickedUp() {
        RestAssured.get("/skill/foobar-skill").then()
                .statusCode(200)
                .body(containsString("This is the foobar skill content."));

        devModeTest.modifyResourceFile("skills/foobar-skill/SKILL.md",
                s -> s.replace("This is the foobar skill content.", "This is the modified foobar skill content."));

        RestAssured.get("/skill/foobar-skill").then()
                .statusCode(200)
                .body(containsString("This is the modified foobar skill content."))
                .body(not(containsString("This is the foobar skill content.")));
    }

    @Path("/skill")
    public static class SkillResource {

        @Inject
        SkillsToolProvider toolProvider;

        @GET
        @Path("/{name}")
        public String activate(@PathParam("name") String name) {
            ToolProviderResult result = toolProvider.provideTools(null);
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("activate_skill")
                    .arguments("{\"skill_name\": \"" + name + "\"}")
                    .build();
            return result.toolExecutorByName("activate_skill").executeWithContext(request, null).resultText();
        }
    }
}
