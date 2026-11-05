package io.quarkiverse.langchain4j.test;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

public class ExecutorProviderDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar.addClass(ExecutorProviderResource.class));

    @Test
    void usesCurrentManagedExecutorAfterReload() {
        get("/executor").then().statusCode(200).body(is("before:true"));
        devModeTest.modifySourceFile(ExecutorProviderResource.class, source -> source.replace("before", "after"));
        get("/executor").then().statusCode(200).body(is("after:true"));
    }
}
