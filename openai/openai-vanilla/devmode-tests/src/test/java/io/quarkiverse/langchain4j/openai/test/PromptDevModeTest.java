package io.quarkiverse.langchain4j.openai.test;

import static io.restassured.RestAssured.get;

import java.util.function.Function;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

public class PromptDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Service.class, Resource.class, Configuration.class, ConfigurationKey.class,
                            Resource.ApplicationGlobals.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.langchain4j.openai.api-key=whatever\nquarkus.langchain4j.openai.base-url= https://mockgpt.wiremockapi.cloud/v1"),
                            "application.properties"));

    @Test
    public void test() {
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
