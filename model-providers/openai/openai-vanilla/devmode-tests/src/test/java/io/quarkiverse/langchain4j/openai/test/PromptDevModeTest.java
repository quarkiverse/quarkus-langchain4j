package io.quarkiverse.langchain4j.openai.test;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusDevModeTest;

public class PromptDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Service.class, ServiceWithResource.class, Resource.class, Configuration.class,
                            ConfigurationKey.class,
                            Resource.ApplicationGlobals.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.mock-api.enabled=true\n"),
                            "application.properties")
                    .addAsResource("cs-organizer.txt"));

    @Test
    public void testJavaChange() {
        try (MockModelServer mock = connect()) {
            mock.reset();
            mock.chat().reply(ChatReply.text("talks"), ChatReply.text("talks"));

            get("test")
                    .then()
                    .statusCode(200);

            devModeTest.modifySourceFile(Resource.class, new Function<String, String>() {
                @Override
                public String apply(String s) {
                    return s.replace("java", "kotlin");
                }
            });

            get("test")
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    public void testResourceChange() {
        try (MockModelServer mock = connect()) {
            mock.reset();
            mock.chat().reply(ChatReply.text("talks"), ChatReply.text("talks"));

            get("test/with-resource")
                    .then()
                    .statusCode(200);

            assertThat(mock.chat().singleRequest().body()).contains("science");

            devModeTest.modifyResourceFile("cs-organizer.txt", new Function<String, String>() {
                @Override
                public String apply(String s) {
                    return s.replace("science", "programming");
                }
            });

            get("test/with-resource")
                    .then()
                    .statusCode(200);

            List<CapturedRequest> requests = mock.chat().requests();
            assertThat(requests).hasSize(2);
            assertThat(requests.get(1).body()).contains("programming");
        }
    }

    // the dev mode test cannot read the application's config because of ClassLoaders, so the application exposes it
    private static MockModelServer connect() {
        return MockModelServer.connect(get("test/url")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString());
    }
}
