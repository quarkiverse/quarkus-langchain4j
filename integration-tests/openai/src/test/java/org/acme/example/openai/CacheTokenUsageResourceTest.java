package org.acme.example.openai;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.net.URL;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class CacheTokenUsageResourceTest {

    @TestHTTPEndpoint(CacheTokenUsageResource.class)
    @TestHTTPResource
    URL url;

    @Test
    public void resolvesOpenAiCacheTokens() {
        given()
                .baseUri(url.toString())
                .get("openai")
                .then()
                .statusCode(200)
                .body(equalTo("read=8;creation=null"));
    }
}
