package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * An application started with no agent announces the address of its HTTP server all the same, and a live reload that
 * brings the first agent keeps that server running without announcing the address again. The card of that first
 * agent still has to name the server.
 */
public class FirstAgentAddedByLiveReloadTest {

    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(FakeChatModel.class, SunnyChatModel.class);
                }
            });

    @Test
    public void testTheFirstAgentCardNamesTheServer() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then().statusCode(not(200));

        test.addSourceFile(LiveReloadAgent.class);

        given()
                .when().get(AGENT_CARD_PATH)
                .then().statusCode(200)
                .body("skills[0].description", equalTo("Helps with weather"))
                .body("supportedInterfaces[0].url", startsWith("http://localhost:"));
    }
}
