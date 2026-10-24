package io.quarkiverse.langchain4j.mockapi.apptests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.langchain4j.mockapi.MockModelApiTestProfile;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(MockModelApiTestProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProfileEnabledTest {

    @Inject
    MockModelServer mock;

    @Test
    @Order(1)
    void scriptedReplyReachesTheEndpoint() {
        mock.chat().reply("scripted");
        given().queryParam("q", "hi").get("/chat").then().statusCode(200).body(is("scripted"));
        assertThat(mock.chat().singleRequest().asChat().messages())
                .anySatisfy(m -> assertThat(m.text()).isEqualTo("hi"));
        assertThat(mock.chat().singleRequest().header("Authorization")).isEqualTo("Bearer my-own-key");
    }

    @Test
    @Order(2)
    void journalWasResetBetweenTests() {
        // the previous test made a request; MockApiResetCallback must have cleared it before this method
        assertThat(mock.requests()).isEmpty();
    }
}
