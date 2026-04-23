package io.quarkiverse.langchain4j.jlama.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Regression test for https://github.com/quarkiverse/quarkus-langchain4j/issues/TODO
 * <p>
 * The devMode {@code jvmOptions} block in runtime/pom.xml previously included
 * {@code enable-native-access=ALL-UNNAMED}. On Quarkus 3.32+, the new
 * {@code ApplicationModelSerializer} resolves {@code ALL-UNNAMED} to a
 * {@code java.lang.Module} object that {@code Json.appendValue()} cannot
 * serialize, throwing {@code IllegalStateException: Unsupported value type: [ALL-UNNAMED]}
 * at {@code @QuarkusTest} bootstrap time.
 */
public class JlamaBootstrapSmokeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.jlama.chat-model.enabled=false\n" +
                                    "quarkus.langchain4j.jlama.embedding-model.enabled=false\n"),
                            "application.properties"));

    @Test
    void bootstrapSucceedsWithJlamaOnClasspath() {
        // If the devMode jvmOptions block contains enable-native-access=ALL-UNNAMED,
        // bootstrap throws IllegalStateException before reaching this point.
    }
}
