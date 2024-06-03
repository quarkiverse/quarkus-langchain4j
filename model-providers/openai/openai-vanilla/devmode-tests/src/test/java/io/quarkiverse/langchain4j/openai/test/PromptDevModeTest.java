package io.quarkiverse.langchain4j.openai.test;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusDevModeTest;

public class PromptDevModeTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Service.class, ServiceWithResource.class, Resource.class, Configuration.class,
                            ConfigurationKey.class,
                            Resource.ApplicationGlobals.class)
                    .addAsResource(
                            new StringAsset(
                                    String.format(
                                            "quarkus.wiremock.devservices.reload=false\nquarkus.langchain4j.openai.base-url=%s",
                                            WiremockAware.wiremockUrlForConfig("/v1"))),
                            "application.properties")
                    .addAsResource("cs-organizer.txt"));

    @Test
    public void testJavaChange() {
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

    @Test
    public void testResourceChange() {
        get("test/with-resource")
                .then()
                .statusCode(200);

        // unfortunately the only way to get the port for a dev mode test is from the application
        int wiremockPort = Integer.parseInt(get("test/wiremock")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString());

        var requestBody = singleLoggedRequest(wiremock(wiremockPort)).getBody();
        assertThat(new String(requestBody)).contains("science");

        devModeTest.modifyResourceFile("cs-organizer.txt", new Function<String, String>() {
            @Override
            public String apply(String s) {
                return s.replace("science", "programming");
            }
        });

        get("test/with-resource")
                .then()
                .statusCode(200);

        assertThat(wiremock(wiremockPort).getServeEvents()).hasSize(2);
        ServeEvent serveEvent = wiremock().getServeEvents().get(0);
        requestBody = serveEvent.getRequest().getBody();
        assertThat(new String(requestBody)).contains("programming");
    }

}
