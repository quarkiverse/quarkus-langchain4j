package io.quarkiverse.langchain4j.skills.test.integration;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class SkillsTest {

    @Test
    public void skillsAreLoadedFromClasspath() {
        RestAssured.given()
                .get("/greeting")
                .then()
                .statusCode(200)
                .body(allOf(
                        containsString("activate_skill: Always greet the user with the word Ahoy."),
                        containsString("read_skill_resource: Ahoy is how sailors say hello.")));
    }
}
