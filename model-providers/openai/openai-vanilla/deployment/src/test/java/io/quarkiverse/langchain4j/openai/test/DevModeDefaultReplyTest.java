package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

public class DevModeDefaultReplyTest {

    @RegisterExtension
    static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DevChatResource.class)
                    .addAsResource(new StringAsset("quarkus.langchain4j.mock-api.enabled=true\n"
                            + "quarkus.langchain4j.mock-api.default-chat-reply=dev says hi\n"
                            + "quarkus.langchain4j.chat-model.provider=openai\n"), "application.properties"));

    @Test
    void defaultReplyServedAndPortStableAcrossReload() {
        String before = RestAssured.get("/dev-chat").then().statusCode(200).extract().asString();
        assertThat(before).isEqualTo("dev says hi");
        String urlBefore = RestAssured.get("/dev-chat/url").then().statusCode(200).extract().asString();
        assertThat(RestAssured.get("/dev-chat/marker").then().statusCode(200).extract().asString())
                .isEqualTo("before-reload");

        test.modifySourceFile(DevChatResource.class, s -> s.replace("\"before-reload\"", "\"after-reload\""));

        // the new marker proves the reload happened
        assertThat(RestAssured.get("/dev-chat/marker").then().statusCode(200).extract().asString())
                .isEqualTo("after-reload");
        assertThat(RestAssured.get("/dev-chat").then().statusCode(200).extract().asString()).isEqualTo("dev says hi");
        assertThat(RestAssured.get("/dev-chat/url").then().statusCode(200).extract().asString()).isEqualTo(urlBefore);
    }
}
