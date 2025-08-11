package org.acme.example.openai.moderation;

import static io.restassured.RestAssured.given;

import java.net.URL;

import org.acme.example.openai.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@EnabledIfSystemProperty(named = "quarkus.test.profile", matches = "cloud-llm", disabledReason = "mockgpt does not implement moderations")
public class ModerationModelResourceTest {

    @TestHTTPEndpoint(ModerationModelResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void blocking() {
        given()
                .baseUri(url.toString())
                .get("blocking")
                .then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("false"));
    }
}
