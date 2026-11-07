package org.acme.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
public class OracleEmbeddingStoreResourceTest {

    private static final String CHARLIE_2018 = "Charlie was born in 2018.";
    private static final String CHARLIE_2015 = "Charlie was born in 2015.";
    private static final String DAVID_2019 = "David was born in 2019.";

    @BeforeEach
    void resetStore() {
        given().delete("/oracle").then().statusCode(204);
        add("1", 2018, CHARLIE_2018);
        add("2", 2015, CHARLIE_2015);
        add("1", 2019, DAVID_2019);
    }

    @Test
    void searchWithoutFilterReturnsSegmentsOfAllUsers() {
        given().queryParam("text", "When was Charlie born?")
                .get("/oracle/search")
                .then().statusCode(200)
                .body("$", hasSize(3))
                .body("$", containsInAnyOrder(CHARLIE_2018, CHARLIE_2015, DAVID_2019));
    }

    @Test
    void searchWithMetadataFilterReturnsOnlySegmentsOfThatUser() {
        given().queryParam("text", "When was Charlie born?")
                .queryParam("userId", "1")
                .get("/oracle/search")
                .then().statusCode(200)
                .body("$", containsInAnyOrder(CHARLIE_2018, DAVID_2019));
    }

    @Test
    void searchWithCombinedStringAndNumericFilter() {
        given().queryParam("text", "When was Charlie born?")
                .queryParam("userId", "1")
                .queryParam("minYear", 2019)
                .get("/oracle/search")
                .then().statusCode(200)
                .body("$", contains(DAVID_2019));
    }

    private void add(String userId, int year, String text) {
        given().contentType(ContentType.TEXT)
                .queryParam("userId", userId)
                .queryParam("year", year)
                .body(text)
                .post("/oracle/segments")
                .then().statusCode(204);
    }
}
