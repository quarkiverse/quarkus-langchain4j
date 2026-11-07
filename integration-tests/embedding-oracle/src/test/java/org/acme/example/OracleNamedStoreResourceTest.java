package org.acme.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
public class OracleNamedStoreResourceTest {

    private static final String PRODUCT = "The Acme rocket skates are our best seller.";
    private static final String DEFAULT_STORE_TEXT = "Charlie was born in 2018.";

    @BeforeEach
    void resetStores() {
        given().delete("/oracle").then().statusCode(204);
        given().delete("/oracle/products").then().statusCode(204);
    }

    @Test
    void namedStoreIsIsolatedFromDefaultStore() {
        given().contentType(ContentType.TEXT).body(PRODUCT)
                .post("/oracle/products").then().statusCode(204);
        given().contentType(ContentType.TEXT).queryParam("userId", "1").queryParam("year", 2018).body(DEFAULT_STORE_TEXT)
                .post("/oracle/segments").then().statusCode(204);

        given().queryParam("text", "best seller")
                .get("/oracle/products")
                .then().statusCode(200)
                .body("$", contains(PRODUCT));

        given().queryParam("text", "best seller")
                .get("/oracle/search")
                .then().statusCode(200)
                .body("$", not(hasItem(PRODUCT)));
    }
}
