package io.quarkiverse.langchain4j.mockapi.apptests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.mockapi.MockModelApiTestProfile;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusIntegrationTest
@TestProfile(MockModelApiTestProfile.class)
class ProfileEnabledIT {

    DevServicesContext ctx;

    @Test
    void scriptedReplyAcrossProcesses() {
        try (MockModelServer mock = MockModelServer
                .connect(ctx.devServicesProperties().get("quarkus.langchain4j.mock-api.url"))) {
            mock.reset();
            mock.chat().reply("from IT");
            given().queryParam("q", "hi").get("/chat").then().statusCode(200).body(is("from IT"));
            // no base URL of its own, so the model targets the Responses API as it would in production
            assertThat(mock.chat().singleRequest().path()).endsWith("/v1/responses");
            assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer my-own-key");
        }
    }
}
