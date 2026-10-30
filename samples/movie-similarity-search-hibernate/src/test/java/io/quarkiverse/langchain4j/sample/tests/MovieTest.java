package io.quarkiverse.langchain4j.sample.tests;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class MovieTest {

    @Test
    public void recommendations() {
        Response movie = given().get("movies/by-title/Shawshank");
        Assertions.assertEquals(200, movie.statusCode());
        long id = movie.jsonPath().getLong("[0].id");
        Assertions.assertEquals(1, id);

        Response recommended = given().get("movies/similar/1");
        Assertions.assertEquals(200, recommended.statusCode());
        ResponseBody body = recommended.body();
        Assertions.assertTrue(body.asString().contains("Green Mile"),
                "The recommendations don't contain the expected movie 'Green Mile': " + body.prettyPrint());
    }
}
