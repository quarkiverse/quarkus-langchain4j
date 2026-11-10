package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * A live reload keeps the HTTP server running, so the address it announced when it started is never announced again,
 * while the reload builds the card from a new container. The card has to keep naming the server's address.
 */
public class AgentCardAfterLiveReloadTest {

    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(LiveReloadAgent.class, FakeChatModel.class, SunnyChatModel.class);
                }
            });

    @Test
    public void testTheCardStillNamesTheServerAfterAReload() {
        String url = given()
                .when().get(AGENT_CARD_PATH)
                .then().statusCode(200)
                .body("skills[0].description", equalTo("Helps with weather"))
                .extract().path("supportedInterfaces[0].url");

        test.modifySourceFile(LiveReloadAgent.class, new Function<String, String>() {
            @Override
            public String apply(String source) {
                return source.replace("Helps with weather", "Helps with climate");
            }
        });

        given()
                .when().get(AGENT_CARD_PATH)
                .then().statusCode(200)
                .body("skills[0].description", equalTo("Helps with climate"))
                .body("supportedInterfaces[0].url", equalTo(url));
    }
}
